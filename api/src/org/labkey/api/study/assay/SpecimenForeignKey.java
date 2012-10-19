/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.StringExpression;

import java.util.*;

/**
 * User: jeckels
 * Date: May 7, 2009
 */
public class SpecimenForeignKey extends LookupForeignKey
{
    private final AssaySchema _schema;
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;
    private final AssayTableMetadata _tableMetadata;
    private final ContainerFilter _studyContainerFilter;

    private static final String ASSAY_SUBQUERY_SUFFIX = "$AssayJoin";
    private static final String SPECIMEN_SUBQUERY_SUFFIX = "$SpecimenJoin";
    private static final String VIAL_SUBQUERY_SUFFIX = "$VialJoin";
    private static final String STUDY_SUBQUERY_SUFFIX = "$StudyJoin";
    private static final String DRAW_DT_COLUMN_NAME = "DrawDT";

    private Container _targetStudyOverride;
    private SimpleFilter _specimenFilter;

    public SpecimenForeignKey(AssaySchema schema, AssayProvider provider, ExpProtocol protocol)
    {
        this(schema, provider, protocol, provider.getTableMetadata(protocol));
    }

    public SpecimenForeignKey(AssaySchema schema, AssayProvider provider, ExpProtocol protocol, AssayTableMetadata tableMetadata)
    {
        super("RowId");
        _schema = schema;
        _provider = provider;
        _protocol = protocol;
        _tableMetadata = tableMetadata;
        _studyContainerFilter = new StudyContainerFilter(schema);
    }

    public TableInfo getLookupTableInfo()
    {
        Container permissionsCheckContainer = _targetStudyOverride != null ? _targetStudyOverride : _schema.getContainer();

        if (!permissionsCheckContainer.hasPermission(_schema.getUser(), ReadPermission.class))
        {
            return null;
        }
        
        UserSchema studySchema = QueryService.get().getUserSchema(_schema.getUser(), permissionsCheckContainer, "study");
        FilteredTable tableInfo = (FilteredTable)studySchema.getTable("Vial", true, true);
        tableInfo.setContainerFilter(_studyContainerFilter);

        String specimenAlias = ExprColumn.STR_TABLE_ALIAS + SPECIMEN_SUBQUERY_SUFFIX;
        String studyAlias = ExprColumn.STR_TABLE_ALIAS + STUDY_SUBQUERY_SUFFIX;
        String targetStudyAlias = ExprColumn.STR_TABLE_ALIAS + ASSAY_SUBQUERY_SUFFIX;

        SQLFragment sql = new SQLFragment();

        FieldKey participantFK = _tableMetadata.getParticipantIDFieldKey();
        FieldKey visitFK = _tableMetadata.getVisitIDFieldKey(TimepointType.VISIT);
        FieldKey dateFK = _tableMetadata.getVisitIDFieldKey(TimepointType.DATE);
        FieldKey drawDateFK = new FieldKey(dateFK.getParent(), DRAW_DT_COLUMN_NAME);
        AssayProtocolSchema assaySchema = AssayService.get().createProtocolSchema(studySchema.getUser(), _schema.getContainer(), _protocol, null);
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(assaySchema.createDataTable(), Arrays.asList(participantFK, visitFK, dateFK, drawDateFK));

        ColumnInfo participantIdCol = columns.get(participantFK);
        ColumnInfo visitIdCol = columns.get(visitFK);
        ColumnInfo dateCol = columns.get(dateFK);
        SqlDialect dialect = tableInfo.getSqlDialect();
        if (dateCol == null)
        {
            // Check for an alternate column name
            dateCol = columns.get(drawDateFK);
        }
        
        if (participantIdCol != null || visitIdCol != null || dateCol != null)
        {
            // We want NULL if there's no match based on specimen id
            sql.append("CASE WHEN (" + studyAlias + ".TimepointType IS NULL) THEN NULL ELSE (CASE WHEN (");
            if (participantIdCol != null)
            {
                // Check if the participants match, or if they're both NULL
                sql.append("(" + specimenAlias + ".ParticipantId = ");

                // See if we need to cast - Postgres 8.3 is picky about these comparisons
                if (participantIdCol.getJavaClass() != String.class)
                {
                    sql.append("CAST(" + targetStudyAlias + "." + participantIdCol.getAlias() + " AS VARCHAR)");
                }
                else
                {
                    sql.append(targetStudyAlias + "." + participantIdCol.getAlias());
                }
                sql.append(" OR ");
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
                    // If we're in a visit-based study, check that both the visits match or are null. Also,
                    // if the assay has a date column and it has a value, it needs to match as well.
                    sql.append("((" + studyAlias + ".TimepointType IS NULL OR " + studyAlias + ".TimepointType = '" + TimepointType.VISIT + "')");
                    sql.append(" AND (" + specimenAlias + ".Visit = " + targetStudyAlias + "." + visitIdCol.getAlias() + " OR (" + specimenAlias + ".Visit IS NULL AND " + targetStudyAlias + "." + visitIdCol.getAlias() + " IS NULL))");
                    if (dateCol != null)
                    {
                        sql.append(" AND (" + targetStudyAlias + "." + dateCol.getAlias() + " IS NULL OR " + dialect.getDateTimeToDateCast(specimenAlias + ".Date") + " = " + dialect.getDateTimeToDateCast(targetStudyAlias + "." + dateCol.getAlias()) + " OR (" + specimenAlias + ".Date IS NULL AND " + targetStudyAlias + "." + dateCol.getAlias() + " IS NULL))");
                        sql.append(")");
                        sql.append(" OR ");
                    }
                    else
                    {
                        sql.append(")");
                    }
                }
                if (dateCol != null)
                {
                    // If we're in a relative date or continuous date study, check that the dates match or are both NULL
                    sql.append("((" + studyAlias + ".TimepointType = '" + TimepointType.DATE + "' OR " + studyAlias + ".TimepointType = '" + TimepointType.CONTINUOUS + "' OR " + studyAlias + ".TimepointType IS NULL) AND (" +
                            dialect.getDateTimeToDateCast(specimenAlias + ".Date") + " = " +
                            dialect.getDateTimeToDateCast(targetStudyAlias + "." + dateCol.getAlias()) + " OR (" + specimenAlias + ".Date IS NULL AND " + targetStudyAlias + "." + dateCol.getAlias() + " IS NULL)))");
                }
                sql.append(")");
            }
            sql.append(") THEN ? ELSE ? END) END");
            sql.add(Boolean.TRUE);
            sql.add(Boolean.FALSE);
        }
        else
        {
            sql.append("CAST(NULL AS " + dialect.getBooleanDataType() + ")");
        }

        tableInfo.addColumn(new ExprColumn(tableInfo, AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME, sql, JdbcType.BOOLEAN));
        tableInfo.setLocked(true);
        return tableInfo;
    }

    public void setTargetStudyOverride(Container targetStudy)
    {
        _targetStudyOverride = targetStudy;
    }

    public void addSpecimenFilter(SimpleFilter filter)
    {
        if (_specimenFilter == null)
            _specimenFilter = new SimpleFilter();
        _specimenFilter.addAllClauses(filter);
    }


    public StringExpression getURL(ColumnInfo parent)
    {
        // SpecimenForeignKeys never have details URLs, and it's very expensive to instantiate a TableInfo to check,
        // so we override the behavior here:
        return null;
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
        if (null == table)
            return null;
        ColumnInfo lookupKey = getPkColumn(table);
        ColumnInfo lookupColumn = table.getColumn(displayField);
        if (null == lookupColumn)
            return null;

        SpecimenLookupColumn ret = new SpecimenLookupColumn(foreignKey, lookupKey, lookupColumn);
        ret.copyAttributesFrom(lookupColumn);
        ret.copyURLFrom(lookupColumn, foreignKey.getFieldKey(), null);
        ret.setLabel("Specimen " + lookupColumn.getLabel());
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
                return _lookupColumn.getValueSql(tableAlias);
            }
            else
            {
                return super.getValueSql(tableAlias);
            }
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            FieldKey targetStudyFK = _tableMetadata.getTargetStudyFieldKey();

            FieldKey participantFK = _tableMetadata.getParticipantIDFieldKey();
            FieldKey specimenFK = _tableMetadata.getSpecimenIDFieldKey();
            FieldKey visitFK = _tableMetadata.getVisitIDFieldKey(TimepointType.VISIT);
            FieldKey dateFK = _tableMetadata.getVisitIDFieldKey(TimepointType.DATE);
            // Need to support this other name for dates
            FieldKey drawDateFK = new FieldKey(dateFK.getParent(), DRAW_DT_COLUMN_NAME);
            FieldKey objectIdFK = _tableMetadata.getResultRowIdFieldKey();

            List<FieldKey> targetStudyFieldKeys = new ArrayList<FieldKey>(Arrays.asList(targetStudyFK, objectIdFK, participantFK, specimenFK, visitFK, dateFK, drawDateFK));
            List<String> foreignPKs = getParentTable().getPkColumnNames();
            for (String foreignPK : foreignPKs)
                targetStudyFieldKeys.add(FieldKey.fromParts(foreignPK));
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(getParentTable(), targetStudyFieldKeys);

            ColumnInfo targetStudyCol = columns.get(targetStudyFK);
            Container targetStudy;
            if (_targetStudyOverride != null)
                targetStudy = _targetStudyOverride;
            else
                targetStudy = _schema.getTargetStudy();

            // Do a complicated join if we can identify a target study so that we choose the right specimen
            if (targetStudyCol != null || targetStudy != null)
            {
                ColumnInfo objectIdCol = columns.get(objectIdFK);
                Sort sort = null;
                if (getParentTable().getSqlDialect().isPostgreSQL())
                {
                    // This sort is a hack to get Postgres to choose a better plan - it flips the query from using a nested loop
                    // join to a merge join on the aggregate query
                    sort = new Sort(objectIdCol.getName());
                }
                // Select all the assay-side specimen columns that we'll need to do the comparison
                SQLFragment targetStudySQL = QueryService.get().getSelectSQL(getParentTable(), columns.values(), null, sort, Table.ALL_ROWS, Table.NO_OFFSET, false);
                SQLFragment sql = new SQLFragment(" LEFT OUTER JOIN (");
                sql.append(targetStudySQL);
                String assaySubqueryAlias = parentAlias + ASSAY_SUBQUERY_SUFFIX;
                String specimenSubqueryAlias = parentAlias + SPECIMEN_SUBQUERY_SUFFIX;
                String vialSubqueryAlias = parentAlias + VIAL_SUBQUERY_SUFFIX;
                String studySubqueryAlias = parentAlias + STUDY_SUBQUERY_SUFFIX;
                ColumnInfo specimenColumnInfo = columns.get(specimenFK);

                sql.append(") AS " + assaySubqueryAlias + " ON ");
                List<ColumnInfo> pks = getParentTable().getPkColumns();
                assert pks.size() > 0;
                String sep = "";
                for (ColumnInfo pk : pks)
                {
                    sql.append(sep);
                    sql.append(assaySubqueryAlias + "." + pk.getName() + " = " + parentAlias + "." + pk.getName());
                    sep = " AND ";
                }

                sql.append("\n\tLEFT OUTER JOIN study.vial AS " + vialSubqueryAlias);
                sql.append(" ON " + vialSubqueryAlias + ".GlobalUniqueId = " + assaySubqueryAlias + "." + specimenColumnInfo.getAlias());
                if (targetStudy != null)
                {
                    // We're in the middle of a copy to study, so ignore what the user selected as the target when they uploaded
                    sql.append(" AND " + vialSubqueryAlias + ".Container = ?");
                    sql.add(targetStudy.getId());
                }
                else
                {
                    // Match based on the target study associated with the assay data
                    sql.append(" AND " + assaySubqueryAlias + "." + targetStudyCol.getAlias() + " = " + vialSubqueryAlias + ".Container");
                }
                // Select all the study-side specimen columns that we'll need to do the comparison
                sql.append("\n\tLEFT OUTER JOIN (SELECT RowId, Container, PTID AS ParticipantId, drawtimestamp AS Date, VisitValue AS Visit ");
                sql.append(" FROM study.Specimen ");
                if (_specimenFilter != null)
                {
                    sql.append(_specimenFilter.getSQLFragment(getParentTable().getSqlDialect()));
                }
                sql.append("\n\t) AS " + specimenSubqueryAlias + " ON " + specimenSubqueryAlias + ".RowId = " + vialSubqueryAlias + ".SpecimenID");
                sql.append("\n\tLEFT OUTER JOIN study.study AS " + studySubqueryAlias);
                sql.append(" ON " + studySubqueryAlias + ".Container = " + specimenSubqueryAlias + ".Container");

                // Last join to the specimen table based on RowId
                sql.append("\n\tLEFT OUTER JOIN ");

                TableInfo lookupTable = _lookupKey.getParentTable();
                String colTableAlias = getTableAlias(parentAlias);
                sql.append(lookupTable.getFromSQL(colTableAlias));
                sql.append(" ON ");
                sql.append(vialSubqueryAlias + ".RowId = " + colTableAlias + ".RowId");

                map.put(specimenSubqueryAlias, sql);
            }
            else
            {
                super.declareJoins(parentAlias, map);
            }
        }
    }
}
