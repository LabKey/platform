/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.study.visualization;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyEntity;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.util.Pair;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.VisualizationIntervalColumn;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.visualization.VisualizationSourceColumn;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 26, 2011 5:10:03 PM
 */
public class StudyVisualizationProvider extends VisualizationProvider<StudyQuerySchema>
{
    public StudyVisualizationProvider(StudyQuerySchema schema)
    {
        super(schema);
    }

    @Override
    public void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery query)
    {
        if (getType() == ChartType.TIME_VISITBASED && !query.isSkipVisitJoin())
        {
            // add the visit, label, and display order to the select list
            String subjectNounSingular = StudyService.get().getSubjectNounSingular(query.getContainer());
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit/Label", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit/DisplayOrder", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/VisitDate", true), false);
        }
    }

    @Override
    public boolean isJoinColumn(VisualizationSourceColumn column, Container container)
    {
        String subjectColName = StudyService.get().getSubjectColumnName(container).toLowerCase();
        String subjectVisitName = StudyService.get().getSubjectVisitColumnName(container).toLowerCase() + "/";

        String name = column.getOriginalName().toLowerCase();

        if (subjectColName.equals(name) || name.startsWith(subjectVisitName) || "container".equals(name))
            return true;

        return false;
    }

    @Override
    public void addExtraResponseProperties(Map<String, Object> extraProperties)
    {
        Map<String, Map<String, Object>> metaData = new HashMap<>();
        Study study = getSchema().getStudy();
        if (study != null)
        {
            int i=1;
            for (Visit visit : study.getVisits(Visit.Order.DISPLAY))
            {
                Map<String, Object> visitInfo = new HashMap<>();

                visitInfo.put("displayOrder", i++);
                visitInfo.put("displayName", visit.getDisplayString());
                visitInfo.put("sequenceNumMin", visit.getSequenceNumMin());
                visitInfo.put("sequenceNumMax", visit.getSequenceNumMax());
                visitInfo.put("description", visit.getDescription());

                metaData.put(visit.getId().toString(), visitInfo);
            }
            extraProperties.put("visitMap", metaData);
        }
    }


    @Override
    public void addExtraColumnProperties(ColumnInfo column, TableInfo table, Map<String, Object> props)
    {
        if (table instanceof DatasetTable)
        {
            List<String> alternateKeys = new ArrayList<>();
            List<ColumnInfo> cols = table.getAlternateKeyColumns();
            for (ColumnInfo col: cols)
            {
                String uri = StringUtils.defaultString(col.getConceptURI(), col.getPropertyURI());
                alternateKeys.add(uri);
            }

            props.put("uniqueKeys", alternateKeys);
        }
    }


    @Override
    public void appendAggregates(StringBuilder sql, Map<String, Set<VisualizationSourceColumn>> columnAliases, Map<String, VisualizationIntervalColumn> intervals, String queryAlias, IVisualizationSourceQuery joinQuery)
    {
        for (Map.Entry<String, VisualizationIntervalColumn> entry : intervals.entrySet())
        {
            sql.append(", ");
            sql.append(queryAlias);
            sql.append(".");
            sql.append(entry.getValue().getSQLAlias(intervals.size()));
        }

        Container container = joinQuery.getContainer();
        String subjectColumnName = StudyService.get().getSubjectNounSingular(container);

        if (getType() == ChartType.TIME_VISITBASED)
        {
            for (String s : Arrays.asList("Visit/Visit","Visit/Visit/DisplayOrder","Visit/Visit/SequenceNumMin","Visit/Visit/Label"))
            {
                Set<VisualizationSourceColumn> cols = columnAliases.get(subjectColumnName + s);
                if (cols != null)
                {
                    VisualizationSourceColumn col = cols.iterator().next();
                    sql.append(", ");
                    sql.append(queryAlias);
                    sql.append(".");
                    sql.append(col.getSQLAlias());
                    if (null != col.getLabel())
                        sql.append(" @title='").append(StringUtils.replace(col.getLabel(), "'", "''")).append("'");
                }
            }
        }
    }


    @Override
    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery first, IVisualizationSourceQuery second, boolean isGroupByQuery)
    {
        if (!first.getContainer().equals(second.getContainer()))
            throw new IllegalArgumentException("Can't yet join across containers.");

        List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinCols = new ArrayList<>();

        String firstSubjectColumnName = StudyService.get().getSubjectColumnName(first.getContainer());
        String firstSubjectNounSingular = StudyService.get().getSubjectNounSingular(first.getContainer());
        // allow null results for this column so as to follow the lead of the primary measure column for this query:
        VisualizationSourceColumn firstSubjectCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectColumnName, true);
        String secondSubjectColName = StudyService.get().getSubjectColumnName(second.getContainer());
        String secondSubjectNounSingular = StudyService.get().getSubjectNounSingular(second.getContainer());
        // allow null results for this column so as to follow the lead of the primary measure column for this query:
        VisualizationSourceColumn secondSubjectCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectColName, true);

        joinCols.add(new Pair<>(firstSubjectCol, secondSubjectCol));

        if (!first.isVisitTagQuery() && ! second.isVisitTagQuery())
        {
            // attempt to lookup the dataset using the queryName by label and then by name
            Dataset firstDataset = StudyService.get().resolveDataset(first.getContainer(), first.getQueryName());
            Dataset secondDataset = StudyService.get().resolveDataset(second.getContainer(), second.getQueryName());

            boolean subjectJoinOnly = isGroupByQuery || first.isSkipVisitJoin() || second.isSkipVisitJoin();

            // if either query is a demographic dataset, it's sufficient to join on subject only:
            if (!subjectJoinOnly && (firstDataset == null || firstDataset.getKeyType() != Dataset.KeyType.SUBJECT) &&
                    (secondDataset == null || secondDataset.getKeyType() != Dataset.KeyType.SUBJECT))
            {
                VisualizationSourceColumn firstSequenceCol = getVisitJoinColumn(factory, first, firstSubjectNounSingular);
                VisualizationSourceColumn secondSequenceCol = getVisitJoinColumn(factory, second, secondSubjectNounSingular);
                joinCols.add(new Pair<>(firstSequenceCol, secondSequenceCol));

                // for datasets with matching 3rd keys, join on subject/visit/key (if neither are pivoted), allowing null results for this column so as to follow the lead of the primary measure column for this query:
                if (firstDataset != null && firstDataset.getKeyType() == Dataset.KeyType.SUBJECT_VISIT_OTHER &&
                        secondDataset != null && secondDataset.getKeyType() == Dataset.KeyType.SUBJECT_VISIT_OTHER &&
                        first.getPivot() == null && second.getPivot() == null && firstDataset.hasMatchingExtraKey(secondDataset))
                {
                    VisualizationSourceColumn firstKeyCol = factory.create(first.getSchema(), first.getQueryName(), firstDataset.getKeyPropertyName(), true);
                    VisualizationSourceColumn secondKeyCol = factory.create(second.getSchema(), second.getQueryName(), secondDataset.getKeyPropertyName(), true);
                    joinCols.add(new Pair<>(firstKeyCol, secondKeyCol));
                }
            }
        }

        return joinCols;
    }

    // for non-demographic datasets, join on subject/visit, allowing null results for this column so as to follow the lead of the primary measure column for this query:
    protected VisualizationSourceColumn getVisitJoinColumn(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery query, String subjectNounSingular)
    {
        String subjectVisit = subjectNounSingular + "Visit";
        String colName = query.getQueryName().equalsIgnoreCase(subjectVisit) ? "" : subjectVisit + "/";
        colName += (getType() == ChartType.TIME_VISITBASED ? "sequencenum" : "VisitDate");
        return factory.create(query.getSchema(), query.getQueryName(), colName, true);
    }

    @Override
    protected boolean isValid(TableInfo table, QueryDefinition query, ColumnMatchType type)
    {
        if (table instanceof DatasetTable)
        {
            if (!((DatasetTable) table).getDataset().isShowByDefault())
                return false;
        }
        if (type == ColumnMatchType.CONFIGURED_MEASURES)
        {
            // custom queries need to contain the ParticipantID and ParticipantVisit columns in order for the joining to work
            return table != null &&
                    table.getColumnNameSet().contains(StudyService.get().getSubjectColumnName(query.getContainer())) &&
                    table.getColumnNameSet().contains(StudyService.get().getSubjectVisitColumnName(query.getContainer()));
        }

        return super.isValid(table, query, type);
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(Map<QueryDefinition, TableInfo> queries, ColumnMatchType type)
    {
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> matches = super.getMatchingColumns(queries, type);
        StudyService studyService = StudyService.get();
        final Container schemaContainer = getSchema().getContainer();

        if (type == ColumnMatchType.DATETIME_COLS)
        {
            Study study = studyService.getStudy(schemaContainer);
            // for visit based studies, we will look for the participantVisit.VisitDate column and
            // if found, return that as a date measure
            if (study != null && study.getTimepointType().isVisitBased())
            {
                for (Map.Entry<QueryDefinition, TableInfo> entry : queries.entrySet())
                {
                    QueryDefinition queryDefinition = entry.getKey();
                    String visitColName = studyService.getSubjectVisitColumnName(schemaContainer);
                    ColumnInfo visitCol = entry.getValue().getColumn(visitColName);
                    if (visitCol != null)
                    {
                        TableInfo visitTable = visitCol.getFkTableInfo();
                        if (visitTable != null)
                        {
                            ColumnInfo visitDate = visitTable.getColumn("visitDate");
                            if (visitDate != null)
                            {
                                FieldKey fieldKey = FieldKey.fromParts(visitColName, visitDate.getName());
                                matches.put(Pair.of(fieldKey, visitDate), queryDefinition);
                            }
                        }
                    }
                }
            }
        }
        else if (type == ColumnMatchType.All_VISIBLE)
        {
            List<Pair<FieldKey, ColumnInfo>> colsToRemove = new ArrayList<>();
            String subjectColName = studyService.getSubjectColumnName(schemaContainer);
            String visitColName = studyService.getSubjectVisitColumnName(schemaContainer);

            // for studies we want to exclude the subject and visit columns
            for (Pair<FieldKey, ColumnInfo> pair : matches.keySet())
            {
                ColumnInfo col = pair.second;
                String columnName = col.getColumnName();
                if (subjectColName.equalsIgnoreCase(columnName) || visitColName.equalsIgnoreCase(columnName) || "DataSets".equals(columnName))
                    colsToRemove.add(pair);
            }

            colsToRemove.forEach(matches::remove);
        }
        return matches;
    }

    @Override
    protected Set<String> getTableNames(UserSchema schema)
    {
        Set<String> tables = new HashSet<>(super.getTableNames(schema));
        tables.remove(StudyQuerySchema.STUDY_DATA_TABLE_NAME);
        return tables;
    }

    @Override
    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getZeroDateMeasures(QueryType queryType)
    {
        // For studies, valid zero date columns are found in demographic datasets only:
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = new HashMap<>();
        Study study = getSchema().getStudy();
        if (study != null)
        {
            study.getDatasets()
                    .stream()
                    .filter(Dataset::isDemographicData)
                    .filter(Dataset::isShowByDefault)
                    .forEach(ds ->
                    {
                        Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(ds.getName(), ColumnMatchType.DATETIME_COLS, false);
                        if (entry != null)
                        {
                            QueryDefinition query = entry.getKey();
                            query.getColumns(null, entry.getValue()).stream()
                                    .filter(col -> col != null && ColumnMatchType.DATETIME_COLS.match(col))
                                    .forEach(col -> measures.put(Pair.of(col.getFieldKey(), col), query));
                        }
                    });
        }
        return measures;
    }

    @Override
    protected Map<QueryDefinition, TableInfo> getQueryDefinitions(QueryType queryType, ColumnMatchType matchType)
    {
        if (queryType == QueryType.datasets)
        {
            Map<QueryDefinition, TableInfo> queries = new HashMap<>();
            Study study = StudyService.get().getStudy(getSchema().getContainer());
            addDatasetQueryDefinitions(study, queries);
            return queries;
        }

        return super.getQueryDefinitions(queryType, matchType);
    }

    @Override
    /**
     * All columns for a study if builtIn types were requested would be constrained to datasets only
     */
    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getAllColumns(QueryType queryType, boolean showHidden)
    {
        if (queryType == QueryType.builtIn || queryType == QueryType.datasets)
        {
            Map<QueryDefinition, TableInfo> queries = new HashMap<>();
            Study study = StudyService.get().getStudy(getSchema().getContainer());
            if (study != null)
            {
                addDatasetQueryDefinitions(study, queries);

                if (queryType == QueryType.builtIn)
                {
                    for (String name : getSchema().getTableAndQueryNames(true))
                    {
                        if (!StringUtils.startsWithIgnoreCase(name, "Primary Type Vial Counts") &&
                            !StringUtils.startsWithIgnoreCase(name, "Primary/Derivative Type Vial Counts") &&
                            !StringUtils.startsWithIgnoreCase(name, "Vial Counts by Requesting Location"))
                            continue;
                        Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(name, ColumnMatchType.All_VISIBLE, false);
                        if (entry != null)
                        {
                            queries.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
            return getMatchingColumns(queries, showHidden ? ColumnMatchType.All : ColumnMatchType.All_VISIBLE);
        }

        return super.getAllColumns(queryType, showHidden);
    }

    private void addDatasetQueryDefinitions(Study study, Map<QueryDefinition, TableInfo> queries)
    {
        if (study != null)
        {
            study.getDatasets()
                    .stream()
                    .filter(StudyEntity::isShowByDefault)
                    .forEach(ds ->
                    {
                        Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(ds.getName(), ColumnMatchType.All_VISIBLE, false);
                        if (entry != null)
                        {
                            queries.put(entry.getKey(), entry.getValue());
                        }
                    });
        }
    }

    @Override
    public String getSourceCountSql(@NotNull JSONArray sources, JSONArray members, String colName)
    {
        // always use the same column ignoring the 'colName' parameter
        String targetColumn = "DataSet.Name";
        String distinctColumn = "ParticipantId";

        String selectSql = "SELECT " + targetColumn + " as label, COUNT(DISTINCT " + distinctColumn + ") AS value FROM StudyData ";
        selectSql += getMemberWhereClause(members, distinctColumn);
        selectSql += "GROUP BY " + targetColumn;

        return selectSql;
    }

    protected String getMemberWhereClause(JSONArray members, String distinctColumn)
    {
        String sql = "", sep = "";

        if (members != null)
        {
            if (members.length() > 0)
            {
                sql += " WHERE " + distinctColumn + " IN (";
                for (int i = 0; i < members.length(); i++)
                {
                    sql += sep + toSqlString(members.getString(i));
                    sep = ", ";
                }
                sql += ") ";
            }
            else
            {
                // empty members array means that there are no patients that match the filters, so force empty results
                sql += " WHERE 1=0 ";
            }
        }

        return sql;
    }

    private String toSqlString(String unescapedSql)
    {
        return "'" + unescapedSql.replaceAll("'", "''") + "'";
    }
}
