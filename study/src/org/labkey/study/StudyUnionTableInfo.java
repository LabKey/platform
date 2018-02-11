/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
* User: jeckels
* Date: Oct 8, 2010
*/
public class StudyUnionTableInfo extends VirtualTable
{
    StudyImpl _study;

    final public static String[] COLUMN_NAMES = {
            "container",
            "participantid",
            "lsid",
            "sequencenum",
            "modified",
            "created",
            "modifiedby",
            "createdby",
            "sourcelsid",
            "_key",
            "_visitdate",
            "qcstate",
            "participantsequencenum"
    };

    final Set<String> unionColumns = new HashSet<>(Arrays.asList(COLUMN_NAMES));
    SQLFragment unionSql;
    private User _user;
    boolean _crossContainer = false;
    Collection<DatasetDefinition> _defs;

    public StudyUnionTableInfo(StudyImpl study, Collection<DatasetDefinition> defs, User user)
    {
        this(study, defs, user, false);
    }

    public StudyUnionTableInfo(StudyImpl study, Collection<DatasetDefinition> defs, User user, boolean crossContainer)
    {
        super(StudySchema.getInstance().getSchema(), "StudyData");
        _study = study;
        _user = user;
        _crossContainer = crossContainer;
        _defs = defs;
        init(defs);
    }

    public void init(Collection<DatasetDefinition> defs)
    {
        SQLFragment sqlf = new SQLFragment();
        int count = 0;
        String unionAll = "";

        Set<PropertyDescriptor> sharedProperties = _study.getSharedProperties();

        for (DatasetDefinition def : defs)
        {
            TableInfo ti = def.getStorageTableInfo();
            if (null == ti || (_user != null && !def.canRead(_user)))
                continue;
            count++;
            sqlf.append(unionAll);
            sqlf.append("SELECT CAST('" + def.getEntityId() + "' AS " + getSqlDialect().getGuidType() + ") AS dataset, " + def.getDatasetId() + " AS datasetid");

            String visitPropertyName = def.getVisitDateColumnName();
            ColumnInfo visitColumn = null == visitPropertyName ? null : ti.getColumn(visitPropertyName);
            if (null != visitPropertyName && (null == visitColumn || visitColumn.getJdbcType() != JdbcType.TIMESTAMP))
                Logger.getLogger(StudySchema.class).info("Could not find visit column of correct type '" + visitPropertyName + "' in dataset '" + def.getName() + "'");
            if (null != visitColumn && visitColumn.getJdbcType() == JdbcType.TIMESTAMP)
                sqlf.append(", ").append(visitColumn.getValueSql("D")).append(" AS _visitdate");
            else
                sqlf.append(", ").append(NullColumnInfo.nullValue(getSqlDialect().getDefaultDateTimeDataType())).append(" AS _visitdate");

            ColumnInfo dayColumn = ti.getColumn("day");
            if (null == dayColumn)
                dayColumn = ti.getColumn("visitday");
            if (null == dayColumn)
                dayColumn = ti.getColumn("visit_day");
            if (null != dayColumn && dayColumn.getJdbcType() == JdbcType.INTEGER)
                sqlf.append(", ").append(dayColumn.getValueSql("D")).append(" AS _visitday");
            else
                sqlf.append(", ").append(NullColumnInfo.nullValue(JdbcType.INTEGER.name())).append(" AS _visitday");

            // Add all of the standard dataset columns
            for (String column : unionColumns)
            {
                if ("_visitdate".equalsIgnoreCase(column))
                    continue;

                ColumnInfo ci = ti.getColumn(column);
                if (!_study.isDataspaceStudy() && "container".equalsIgnoreCase(column))
                {
                    SQLFragment containerSql = new SQLFragment(" CAST(");
                    containerSql.append("'").append(def.getContainer().getId()).append("'");
                    containerSql.append(" AS UNIQUEIDENTIFIER) AS container");
                    ci = new ExprColumn(this, "container", containerSql, JdbcType.GUID);
                }

                if (null == ci)
                    throw new RuntimeException("Schema consistency problem, column not found: " + def.getName() + "." + column);
                sqlf.append(", ").append(ci.getValueSql("D"));
            }

            // Add all of the properties that are common to all datasets
            Set<String> addedProperties = new CaseInsensitiveHashSet();
            for (PropertyDescriptor pd : sharedProperties)
            {
                // Avoid double-adding columns with the same name but different property descriptors
                if (addedProperties.add(pd.getName()))
                {
                    ColumnInfo col = ti.getColumn(pd.getName());
                    if (col != null)
                    {
                        sqlf.append(", ").append(col.getValueSql("D"));
                    }
                    else
                    {
                        // in at least Postgres, it isn't legal to union two columns where one is NULL and the other is
                        // numeric. It's OK when the other column is text.
                        String empty;
                        switch (pd.getSqlTypeInt())
                        {
                            case Types.BIGINT:
                            case Types.INTEGER:
                            case Types.NUMERIC:
                            case Types.DOUBLE:
                            case Types.DECIMAL:
                            case Types.FLOAT:
                            case Types.TIMESTAMP:
                            case Types.TIME:
                            case Types.DATE:
                            case Types.BOOLEAN:
                                empty = "CAST(NULL AS " + getSqlDialect().sqlTypeNameFromSqlType(pd.getSqlTypeInt()) + ")";
                                break;
                            default:
                                empty = "NULL";
                        }

                        sqlf.append(String.format(", %s AS %s", empty, getSqlDialect().makeLegalIdentifier(pd.getName())));
                    }
                }
            }

            sqlf.append(" FROM " + ti.getSelectName() + " D");
            if (def.isShared() && !_crossContainer)
            {
                if (def.getDataSharingEnum() == DatasetDefinition.DataSharing.NONE)
                {
                    sqlf.append(" WHERE container=").append(def.getContainer());
                }
                else
                {
                    sqlf.append(" WHERE container=").append(def.getContainer().getProject()).append(" AND ");
                    sqlf.append(" participantid IN (select participantid from study.participant where container=").append(def.getContainer()).append(")");
                }
            }
            unionAll = ") UNION ALL\n(";
        }

        if (0 == count)
        {
            sqlf.append("SELECT CAST(NULL AS " + getSqlDialect().getGuidType() + ") as dataset, 0 as datasetid");
            for (String column : unionColumns)
            {
                sqlf.append(", ");
                if ("qcstate".equalsIgnoreCase(column) || "sequencenum".equalsIgnoreCase(column) || "createdby".equalsIgnoreCase(column) || "modifiedby".equalsIgnoreCase(column))
                    sqlf.append("0");
                else if ("participantid".equalsIgnoreCase(column) || "participantsequencenum".equalsIgnoreCase(column))
                    sqlf.append("CAST(NULL as VARCHAR)");
                else if ("_visitdate".equalsIgnoreCase(column) || "modified".equalsIgnoreCase(column) || "created".equalsIgnoreCase(column))
                    sqlf.append("CAST(NULL AS " + getSchema().getSqlDialect().getDefaultDateTimeDataType() + ")");
                else if ("container".equalsIgnoreCase(column))
                    sqlf.append("CAST('" + _study.getContainer().getId() + "' AS " + getSqlDialect().getGuidType() + ")");
                else
                    sqlf.append(" NULL");
                sqlf.append(" AS " + column);
            }
            sqlf.append(" WHERE 0=1");
        }

        unionSql = new SQLFragment();
        unionSql.appendComment("<StudyUnionTableInfo>", getSchema().getSqlDialect());
        if (count > 1)
            unionSql.append("(");
        unionSql.append(sqlf);
        if (count > 1)
            unionSql.append(")");
        unionSql.appendComment("</StudyUnionTableInfo>", getSchema().getSqlDialect());
        makeColumnInfos(sharedProperties);
    }


    public SQLFragment getParticipantSequenceNumSQL(String alias)
    {
        Set<FieldKey> fks = new HashSet<>(Arrays.asList(FieldKey.fromParts("participantId"), FieldKey.fromParts("SequenceNum"), FieldKey.fromParts("ParticipantSequenceNum")));
        return _getFromSQL(alias, fks, true);
    }

    @Override
    public SQLFragment getFromSQL(String alias, Set<FieldKey> cols)
    {
        return _getFromSQL(alias, cols, false);
    }

    private SQLFragment _getFromSQL(String alias, Set<FieldKey> cols, boolean distinct)
    {
        if (cols.size() != 2 ||
                !cols.contains(new FieldKey(null,"participantid")) ||
                !cols.contains(new FieldKey(null,"sequencenum")))
            return getFromSQL(alias);

        int count = 0;
        String unionAll = "";
        SQLFragment sqlf = new SQLFragment();
        for (DatasetDefinition def : _defs)
        {
            TableInfo ti = def.getStorageTableInfo();
            if (null == ti || (_user != null && !def.canRead(_user)))
                continue;
            count++;
            sqlf.append(unionAll);
            sqlf.append("SELECT ParticipantId, SequenceNum FROM " + ti.getSelectName() + " _");
            if (def.isShared() && !_crossContainer)
            {
                sqlf.append(" WHERE container=?");
                sqlf.add(def.getContainer());
            }
            if (distinct)
                unionAll = ") UNION \n(";
            else
                unionAll = ") UNION ALL\n(";
        }
        if (count == 0)
            return getFromSQL(alias);

        unionSql = new SQLFragment();
        unionSql.appendComment("<StudyUnionTableInfo>", getSchema().getSqlDialect());

        if (count > 1)
        {
            unionSql.append("((");
            unionSql.append(sqlf);
            unionSql.append(")) ").append(alias);
        }
        else if (distinct)
        {
            unionSql.append("(SELECT DISTINCT ParticipantId, SequenceNum FROM (");
            unionSql.append(sqlf);
            unionSql.append(" _u) ").append(alias);
        }
        else
        {
            unionSql.append("(");
            unionSql.append(sqlf);
            unionSql.append(") ").append(alias);
        }
        unionSql.appendComment("</StudyUnionTableInfo>", getSchema().getSqlDialect());
        return unionSql;
    }


    @Override
    public String getSelectName()
    {
        return null;
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL()
    {
        return unionSql;
    }


    private void makeColumnInfos(Set<PropertyDescriptor> sharedProperties)
    {
        TableInfo template = DatasetDefinition.getTemplateTableInfo();

        for (String name : COLUMN_NAMES)
        {
            ColumnInfo ci = new ColumnInfo(name, this);
            ColumnInfo t = template.getColumn(name);
            if (null != t)
            {
                ci.setExtraAttributesFrom(t);
                ci.setSqlTypeName(t.getSqlTypeName());
            }
            addColumn(ci);
        }

        for (PropertyDescriptor pd : sharedProperties)
        {
            ColumnInfo ci = new ColumnInfo(pd.getName(), this);
            PropertyColumn.copyAttributes(_user, ci, pd, _study.getContainer(), null);

            String oldName = StudySchema.getInstance().getSqlDialect().sqlTypeNameFromSqlType(pd.getSqlTypeInt());
            String newName = StudySchema.getInstance().getSqlDialect().sqlTypeNameFromJdbcType(pd.getJdbcType());

            assert oldName.equals(newName);

            ci.setSqlTypeName(newName);
            safeAddColumn(ci);
        }

        ColumnInfo datasetColumn = new ColumnInfo(new FieldKey(null,"dataset"), this, JdbcType.VARCHAR);
        addColumn(datasetColumn);
        ColumnInfo datasetIdColumn = new ColumnInfo(new FieldKey(null, "datasetid"), this, JdbcType.INTEGER);
        addColumn(datasetIdColumn);
    }

    @Override
    public String toString()
    {
        return "StudyData UNION table";
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null)
        {
            // Resolve 'ParticipantSequenceKey' to 'ParticipantSequenceNum' for compatibility with versions <12.2.
            if ("ParticipantSequenceKey".equalsIgnoreCase(name))
                return getColumn("ParticipantSequenceNum");
        }

        return result;
    }
}
