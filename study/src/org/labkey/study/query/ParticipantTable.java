/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.TitleForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.Set;

public class ParticipantTable extends BaseStudyTable
{
    public static final String ALIASES_COLUMN_NAME = "Aliases";
    private static final String LINKED_IDS_COLUMN_NAME = "LinkedIDs";

    private final StudyImpl _study;

    private Set<String> _participantAliasSources;

    private static final String ALIAS_INNER_QUERY_ALIAS = "X";

    public ParticipantTable(StudyQuerySchema schema, boolean hideDatasets)
    {
        super(schema, StudySchema.getInstance().getTableInfoParticipant());
        setName(StudyService.get().getSubjectTableName(schema.getContainer()));

        _study = StudyManager.getInstance().getStudy(schema.getContainer());
        ColumnInfo rowIdColumn = new AliasedColumn(this, StudyService.get().getSubjectColumnName(getContainer()), _rootTable.getColumn("ParticipantId"));
        rowIdColumn.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
        rowIdColumn.setFk(new TitleForeignKey(getBaseDetailsURL(), null, null, "participantId", getContainerContext()));
        addColumn(rowIdColumn);

        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("ParticipantId"));
        datasetColumn.setKeyField(false);
        datasetColumn.setIsUnselectable(true);
        datasetColumn.setLabel("DataSet");
        datasetColumn.setFk(new AbstractForeignKey()
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                return new ParticipantDatasetTable(_userSchema, parent).getColumn(displayField);
            }

            public TableInfo getLookupTableInfo()
            {
                return new ParticipantDatasetTable(_userSchema, null);
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        addColumn(datasetColumn);
        datasetColumn.setHidden(hideDatasets);

        addContainerColumn();

        ColumnInfo currentCohortColumn;
        boolean showCohorts = StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        if (!showCohorts)
        {
            currentCohortColumn = new NullColumnInfo(this, "Cohort", JdbcType.INTEGER);
            currentCohortColumn.setHidden(true);
        }
        else
        {
            currentCohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CurrentCohortId"));
        }
        currentCohortColumn.setFk(new CohortForeignKey(_userSchema, showCohorts, currentCohortColumn.getLabel()));
        addColumn(currentCohortColumn);


        ColumnInfo initialCohortColumn;
        if (!showCohorts)
        {
            initialCohortColumn = new NullColumnInfo(this, "InitialCohort", JdbcType.INTEGER);
            initialCohortColumn.setHidden(true);
        }
        else if (null != _study && _study.isAdvancedCohorts())
        {
            initialCohortColumn = new AliasedColumn(this, "InitialCohort", _rootTable.getColumn("InitialCohortId"));
        }
        else
        {
            initialCohortColumn = new AliasedColumn(this, "InitialCohort", _rootTable.getColumn("CurrentCohortId"));
            initialCohortColumn.setHidden(true);
        }
        initialCohortColumn.setFk(new CohortForeignKey(_userSchema, showCohorts, initialCohortColumn.getLabel()));
        addColumn(initialCohortColumn);

        ForeignKey fkSite = LocationTable.fkFor(_userSchema);
        addColumn(new AliasedColumn(this, "EnrollmentLocationId", _rootTable.getColumn("EnrollmentSiteId"))).setFk(fkSite);
        addColumn(new AliasedColumn(this, "CurrentLocationId", _rootTable.getColumn("CurrentSiteId"))).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("StartDate"));
        setTitleColumn(StudyService.get().getSubjectColumnName(getContainer()));

        setDetailsURL(new DetailsURL(getBaseDetailsURL(), "participantId",
                FieldKey.fromParts(StudyService.get().getSubjectColumnName(_userSchema.getContainer()))));

        setDefaultVisibleColumns(getDefaultVisibleColumns());

        addAliasesColumn();
        addStudyColumn();

        // join in participant categories
        for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), _userSchema.getUser()))
        {
            ColumnInfo categoryColumn = new ParticipantCategoryColumn(category, this);
            if (!_columnMap.containsKey(categoryColumn.getName()))
                addColumn(categoryColumn);
        }
    }

    private void addAliasesColumn()
    {
        if (_study != null && _study.getParticipantAliasDatasetId() != null)
        {
            DatasetDefinition dataset = _study.getDataset(_study.getParticipantAliasDatasetId());
            User user = getUserSchema().getUser();
            if (dataset != null && dataset.canRead(user))
            {
                // Get the table and the two admin-configured columns
                final DatasetDefinition.DatasetSchemaTableInfo datasetTable = dataset.getTableInfo(user, true);
                final ColumnInfo aliasColumn = datasetTable.getColumn(_study.getParticipantAliasProperty());
                final ColumnInfo sourceColumn = datasetTable.getColumn(_study.getParticipantAliasSourceProperty());

                if (aliasColumn != null && sourceColumn != null)
                {
                    // Make the SQL that will be used to build the concatenated aliases value
                    // Need to do this with a subquery so that SQLServer is happy with the DISTINCT and ORDER BY
                    SQLFragment concatSQL = new SQLFragment("SELECT AliasValue ");
                    concatSQL.append(" FROM (SELECT DISTINCT ");
                    concatSQL.append(aliasColumn.getValueSql(ALIAS_INNER_QUERY_ALIAS));
                    concatSQL.append(" AS AliasValue FROM ");
                    concatSQL.append(datasetTable.getFromSQL(ALIAS_INNER_QUERY_ALIAS));
                    concatSQL.append(" WHERE ");
                    concatSQL.append(ALIAS_INNER_QUERY_ALIAS);
                    concatSQL.append(".ParticipantID = ");
                    concatSQL.append(ExprColumn.STR_TABLE_ALIAS);
                    concatSQL.append(".ParticipantID) Y ORDER BY AliasValue");

                    ExprColumn aliasesColumn = new ExprColumn(this, ALIASES_COLUMN_NAME, getSqlDialect().getSelectConcat(concatSQL, MultiValuedRenderContext.VALUE_DELIMITER), JdbcType.VARCHAR);
                    aliasesColumn.setDisplayColumnFactory(colInfo -> {
                        // Use a multi valued implementation so that we get nice formatting
                        return new MultiValuedDisplayColumn(new DataColumn(colInfo, false));
                    });
                    addColumn(aliasesColumn);

                    // Add a separate column that pivots out the individual aliases based on source
                    ColumnInfo linkedIDsColumn = wrapColumn(LINKED_IDS_COLUMN_NAME, getRealTable().getColumn("ParticipantID"));
                    linkedIDsColumn.setFk(new PivotedAliasForeignKey(datasetTable, sourceColumn, aliasColumn));
                    linkedIDsColumn.setIsUnselectable(true);
                    addColumn(linkedIDsColumn);
                }
            }
        }
    }

    /** Get all of the different sources that are in current use. Source names are case insensitive */
    private Set<String> getParticipantAliasSources(TableInfo datasetTable, ColumnInfo sourceColumn)
    {
        if (_participantAliasSources == null)
        {
            SQLFragment sql = new SQLFragment("SELECT DISTINCT ");
            sql.append(sourceColumn.getValueSql(ALIAS_INNER_QUERY_ALIAS));
            sql.append(" FROM ");
            sql.append(datasetTable.getFromSQL(ALIAS_INNER_QUERY_ALIAS));
            // Use a case-insensitive set because want separate columns for each source, and column names are case
            // insensitive. When selecting out the values for each source via SQL, we do a case insensitive comparison.
            _participantAliasSources = new CaseInsensitiveTreeSet(new SqlSelector(datasetTable.getSchema(), sql).getCollection(String.class));
        }
        return _participantAliasSources;
    }

    public ActionURL getBaseDetailsURL()
    {
        return new ActionURL(StudyController.ParticipantAction.class, _userSchema.getContainer());
    }


    public static class ParticipantCategoryColumn extends ExprColumn
    {
        private final ParticipantCategoryImpl _def;

        public ParticipantCategoryColumn(ParticipantCategoryImpl def, FilteredTable parent)
        {
            super(parent, def.getLabel(), new SQLFragment(), JdbcType.VARCHAR);
            _def = def;
        }

        @Override
        public SQLFragment getValueSql(String parentAlias)
        {
            Container c = ContainerManager.getForId(_def.getContainerId());
            Study s = null==c ? null : StudyManager.getInstance().getStudy(c);
            if (null == s)
                return new SQLFragment("NULL");

            if (s.getShareDatasetDefinitions())
            {
                // NOTE: for dataspace the participantGroupMap rows may come from multiple containers so we can't use
                // _m.Container=?
                // Also, there is a unique constraint on (groupid,participantid,container)
                // but not on (groupid, participantid)
                // to avoid SQL exceptions we change Label to MIN(Label)
                SQLFragment sql = new SQLFragment();
                sql.appendComment("<ParticipantTable: " + _def.getLabel() + ">", getSqlDialect());
                sql.append("(SELECT MIN(Label) FROM ");
                sql.append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup(), "_g" );
                sql.append(" JOIN ");
                sql.append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "_m");
                sql.append(" ON _g.CategoryId = ? AND _g.RowId = _m.GroupId AND _g.Container=").append(c);
                sql.append("WHERE _m.ParticipantId = ").append(parentAlias).append(".ParticipantId)");
                sql.add(_def.getRowId());
                sql.appendComment("</ParticipantTable: " + _def.getLabel() + ">", getSqlDialect());
                return sql;

            }
            else
            {
                SQLFragment sql = new SQLFragment();
                sql.appendComment("<ParticipantTable: " + _def.getLabel() + ">", getSqlDialect());
                sql.append("(SELECT Label FROM ");
                sql.append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup(), "_g");
                sql.append(" JOIN ");
                sql.append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "_m");
                sql.append(" ON _g.CategoryId = ? AND _g.RowId = _m.GroupId AND _g.Container=? AND _m.Container=?\n");
                sql.append("WHERE _m.ParticipantId = ").append(parentAlias).append(".ParticipantId)");
                sql.add(_def.getRowId());
                sql.add(_def.getContainerId());
                sql.add(_def.getContainerId());
                sql.appendComment("</ParticipantTable: " + _def.getLabel() + ">", getSqlDialect());
                return sql;
            }
        }
    }


    @Override
    public ContainerContext getContainerContext()
    {
        return new ContainerContext.FieldKeyContext(new FieldKey(null,"Container"));
    }


    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        if ("EnrollmentSiteId".equalsIgnoreCase(name))
            return getColumn("EnrollmentLocationId");
        if ("CurrentSiteId".equalsIgnoreCase(name))
            return getColumn("CurrentLocationId");
        return super.resolveColumn(name);
    }

    /**
     * A custom FK that pivots out aliases into separate columns per source.
     * We don't expose the lookup target as a separate query.
     */
    private class PivotedAliasForeignKey extends AbstractForeignKey
    {
        private final TableInfo _datasetTable;
        private final ColumnInfo _sourceColumn;
        private final ColumnInfo _aliasColumn;

        public PivotedAliasForeignKey(TableInfo datasetTable, ColumnInfo sourceColumn, ColumnInfo aliasColumn)
        {
            super(StudyQuerySchema.SCHEMA_NAME, "PivotedParticipantAliases", null);
            _datasetTable = datasetTable;
            _sourceColumn = sourceColumn;
            _aliasColumn = aliasColumn;
            setPublic(false);
        }

        public ColumnInfo createLookupColumn(final ColumnInfo parent, String displayField)
        {
            if (displayField == null)
                return null;
            for (final String source : getParticipantAliasSources(_datasetTable, _sourceColumn))
            {
                if (displayField.equalsIgnoreCase(source))
                {
                    // There should be zero or one one value per participant/source combination, so we could either use MIN/MAX
                    SQLFragment sql = new SQLFragment("(SELECT MAX(");
                    sql.append(_aliasColumn.getValueSql(ALIAS_INNER_QUERY_ALIAS));
                    sql.append(") FROM ");
                    sql.append(_datasetTable.getFromSQL(ALIAS_INNER_QUERY_ALIAS));
                    // Do a LOWER on the source column in case there are multiple casings stored as values
                    sql.append(" WHERE LOWER(");
                    sql.append(_sourceColumn.getValueSql(ALIAS_INNER_QUERY_ALIAS));
                    sql.append(") = ? AND ");
                    sql.add(source.toLowerCase());
                    sql.append(ALIAS_INNER_QUERY_ALIAS);
                    sql.append(".ParticipantID = ");
                    sql.append(ExprColumn.STR_TABLE_ALIAS);
                    sql.append(".ParticipantID)");

                    return new ExprColumn(parent.getParentTable(), source, sql, JdbcType.VARCHAR, parent);
                }
            }
            return null;
        }

        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        public TableInfo getLookupTableInfo()
        {
            // Create a simple virtual table so that we can expose one column per alias source
            VirtualTable result = new VirtualTable(getSchema(), null);
            for (String source : getParticipantAliasSources(_datasetTable, _sourceColumn))
            {
                ColumnInfo column = new ColumnInfo(source);
                column.setParentTable(result);
                column.setSqlTypeName(JdbcType.VARCHAR.toString());
                result.safeAddColumn(column);
            }
            return result;
        }
    }


    /* You would usually want to turn off session participantgroup for the whole schema,
     * however, you might want to also turn off just for ParticpantTable when this table
     * is being used as a lookup (especially for a table that is already filtered)
     */
    boolean _ignoreSessionParticipantGroup = false;

    public void setIgnoreSessionParticipantGroup()
    {
        _ignoreSessionParticipantGroup = true;
    }

    protected SimpleFilter getFilter()
    {
        SimpleFilter sf = super.getFilter();

        ParticipantGroup group = _ignoreSessionParticipantGroup ? null : getUserSchema().getSessionParticipantGroup();
        if (null == group)
            return sf;

        SimpleFilter ret = new SimpleFilter();
        ret.addAllClauses(sf);

        FieldKey participantFieldKey = FieldKey.fromParts("ParticipantId");
        ret.addClause(new ParticipantGroupFilterClause(participantFieldKey, group));
        return ret;
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment ret;
        ret = super.getFromSQL(alias);
        return ret;
    }
}
