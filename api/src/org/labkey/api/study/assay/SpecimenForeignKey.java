/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.study.TimepointType;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: May 7, 2009
 */
public class SpecimenForeignKey extends LookupForeignKey
{
    private final QuerySchema _schema;
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;
    private final ContainerFilter _studyContainerFilter;

    private static final String TARGET_STUDY_SUFFIX = "$TargetStudyJoin";
    private static final String SPECIMEN_SUFFIX = "$SpecimenJoin";

    public SpecimenForeignKey(QuerySchema schema, AssayProvider provider, ExpProtocol protocol)
    {
        super("RowId");
        _schema = schema;
        _provider = provider;
        _protocol = protocol;
        _studyContainerFilter = new StudyContainerFilter();
    }

    private class StudyContainerFilter extends ContainerFilter
    {
        private Set<String> _ids;

        public Collection<String> getIds(Container currentContainer)
        {
            if (_ids == null)
            {
                _ids = ContainerFilter.toIds(AssayPublishService.get().getValidPublishTargets(_schema.getUser(), ACL.PERM_READ).keySet());
            }
            return _ids;
        }

        public Type getType()
        {
            throw new UnsupportedOperationException();
        }
    }

    public TableInfo getLookupTableInfo()
    {
        UserSchema studySchema = QueryService.get().getUserSchema(_schema.getUser(), _schema.getContainer(), "study");
        FilteredTable tableInfo = (FilteredTable)studySchema.getTable("SimpleSpecimen");
        tableInfo.setContainerFilter(_studyContainerFilter);

        String specimenAlias = ExprColumn.STR_TABLE_ALIAS + SPECIMEN_SUFFIX;
        String targetStudyAlias = ExprColumn.STR_TABLE_ALIAS + TARGET_STUDY_SUFFIX;

        SQLFragment sql = new SQLFragment();

        AssayTableMetadata tableMetadata = _provider.getTableMetadata();
        FieldKey participantFK = tableMetadata.getParticipantIDFieldKey();
        FieldKey visitFK = tableMetadata.getVisitIDFieldKey(TimepointType.VISIT);
        FieldKey dateFK = tableMetadata.getVisitIDFieldKey(TimepointType.DATE);
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(_provider.createDataTable(studySchema, _protocol), Arrays.asList(participantFK, visitFK, dateFK));

        ColumnInfo participantIdCol = columns.get(participantFK);
        ColumnInfo visitIdCol = columns.get(visitFK);
        ColumnInfo dateCol = columns.get(dateFK);
        if (participantIdCol != null || visitIdCol != null || dateCol != null)
        {
            sql.append("CASE WHEN (");
            if (participantIdCol != null)
            {
                sql.append("(" + specimenAlias + ".ParticipantId = " + targetStudyAlias + "." + participantIdCol.getAlias() + " OR ");
                sql.append("(" + specimenAlias + ".ParticipantId IS NULL AND " + targetStudyAlias + "." + participantIdCol.getAlias() + " IS NULL))");
            }
            if (visitIdCol != null || dateCol != null)
            {
                if (participantIdCol != null)
                {
                    sql.append(" AND ");
                }
                sql.append("(");
                if (visitIdCol != null)
                {
                    sql.append("(" + specimenAlias + ".DateBased = ? AND (" + specimenAlias + ".Visit = " + targetStudyAlias + "." + visitIdCol.getAlias() + " OR (" + specimenAlias + ".Visit IS NULL AND " + targetStudyAlias + "." + visitIdCol.getAlias() + " IS NULL)))");
                    sql.add(Boolean.FALSE);
                    if (dateCol != null)
                    {
                        sql.append(" OR ");
                    }
                }
                if (dateCol != null)
                {
                    SqlDialect dialect = tableInfo.getSqlDialect();
                    sql.append("(" + specimenAlias + ".DateBased = ? AND (" +
                            dialect.getDateTimeToDateCast(specimenAlias + ".Date") + " = " +
                            dialect.getDateTimeToDateCast(targetStudyAlias + "." + dateCol.getAlias()) + " OR (" + specimenAlias + ".Date IS NULL AND " + targetStudyAlias + "." + dateCol.getAlias() + " IS NULL)))");
                    sql.add(Boolean.TRUE);
                }
                sql.append(")");
            }
            sql.append(") THEN ? ELSE ? END");
            sql.add(Boolean.TRUE);
            sql.add(Boolean.FALSE);
        }
        else
        {
            sql.append("NULL");
        }

        tableInfo.addColumn(new ExprColumn(tableInfo, AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME, sql, Types.BOOLEAN));
        return tableInfo;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
    {
        if (displayField == null)
        {
            // Don't show the lookup's value for the base column, so we can filter on it and we don't get
            // brackets around specimen ids that don't match up with the target study
            return foreignKey;
        }
        TableInfo table = getLookupTableInfo();
        
        ColumnInfo lookupKey = getPkColumn(table);
        ColumnInfo lookupColumn = table.getColumn(displayField);
        if (lookupColumn == null)
        {
            return null;
        }

        SpecimenLookupColumn ret = new SpecimenLookupColumn(foreignKey, lookupKey, lookupColumn);
        ret.copyAttributesFrom(lookupColumn);
        ret.setCaption("Specimen " + lookupColumn.getCaption());
        return ret;
    }

    public class SpecimenLookupColumn extends LookupColumn
    {
        public SpecimenLookupColumn(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn)
        {
            super(foreignKey, lookupKey, lookupColumn);
        }

        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            // We want the left hand table, not the lookup that we're joining to
            if (getFieldKey().getName().equals(AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME))
            {
                return lookupColumn.getValueSql(tableAlias);
            }
            else
            {
                return super.getValueSql(tableAlias);
            }
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            AssayTableMetadata tableMetadata = _provider.getTableMetadata();
            FieldKey batchFK = new FieldKey(tableMetadata.getRunRowIdFieldKeyFromResults().getParent(), AssayService.BATCH_COLUMN_NAME);
            FieldKey batchPropertiesFK = new FieldKey(batchFK, AssayService.BATCH_PROPERTIES_COLUMN_NAME);
            FieldKey targetStudyFK = new FieldKey(batchPropertiesFK, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);

            FieldKey participantFK = tableMetadata.getParticipantIDFieldKey();
            FieldKey visitFK = tableMetadata.getVisitIDFieldKey(TimepointType.VISIT);
            FieldKey dateFK = tableMetadata.getVisitIDFieldKey(TimepointType.DATE);
            FieldKey objectIdFK = tableMetadata.getResultRowIdFieldKey();
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(getParentTable(), Arrays.asList(targetStudyFK, objectIdFK, participantFK, visitFK, dateFK));

            ColumnInfo targetStudyCol = columns.get(targetStudyFK);
            if (targetStudyCol != null)
            {
                ColumnInfo objectIdCol = columns.get(objectIdFK);
                SQLFragment targetStudySQL = QueryService.get().getSelectSQL(getParentTable(), columns.values(), null, null, Table.ALL_ROWS, 0);
                SQLFragment sql = new SQLFragment("LEFT OUTER JOIN (");
                sql.append(targetStudySQL);
                String targetStudyAlias = parentAlias + TARGET_STUDY_SUFFIX;
                String specimenAlias = parentAlias + SPECIMEN_SUFFIX;
                ColumnInfo parentKeyCol = foreignKey.getParentTable().getPkColumns().get(0);
                sql.append(") AS " + targetStudyAlias + " ON " + targetStudyAlias + "." + objectIdCol.getAlias() + " = " + parentAlias + "." + parentKeyCol.getName());
                sql.append(" LEFT OUTER JOIN ");
                sql.append(" (SELECT specimen.RowId, specimen.GlobalUniqueId, specimen.Container, specimen.PTID AS ParticipantId, specimen.drawtimestamp AS Date, specimen.VisitValue AS Visit, s.DateBased ");
                sql.append(" FROM study.specimen specimen, study.study s WHERE s.Container = specimen.Container) AS " + specimenAlias);
                sql.append(" ON " + specimenAlias + ".GlobalUniqueId = " + foreignKey.getValueSql(parentAlias));
                sql.append(" AND " + targetStudyAlias + "." + targetStudyCol.getAlias() + " = " + specimenAlias + ".Container"); 

                sql.append("\n\tLEFT OUTER JOIN ");

                TableInfo lookupTable = lookupKey.getParentTable();
                String selectName = lookupTable.getSelectName();
                if (null != selectName)
                    sql.append(selectName);
                else
                {
                    sql.append("(");
                    sql.append(lookupTable.getFromSQL());
                    sql.append(")");
                }

                String colTableAlias = getTableAlias();
                sql.append(" AS ").append(colTableAlias);
                sql.append(" ON ");
                sql.append(specimenAlias + ".RowId = " + getTableAlias() + ".RowId");

                map.put(specimenAlias, sql);
            }
            else
            {
                super.declareJoins(parentAlias, map);
            }
        }
    }
}
