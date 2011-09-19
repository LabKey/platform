package org.labkey.visualization.sql;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.visualization.VisualizationController;

import java.util.*;

/**
* Copyright (c) 2011 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* <p/>
* User: brittp
* Date: Jan 26, 2011 5:10:03 PM
*/
public class StudyVisualizationProvider extends VisualizationProvider
{
    public StudyVisualizationProvider()
    {
        super("study");
    }

    @Override
    public void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery query)
    {
        if (getType() == VisualizationSQLGenerator.ChartType.TIME_VISITBASED)
        {
            // add the visit sequencenum, label, and display order to the select list
            String subjectNounSingular = StudyService.get().getSubjectNounSingular(query.getContainer());
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/sequencenum", true));
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit/Label", true));
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit/DisplayOrder", true));
        }
    }

    @Override
    public void appendAggregates(StringBuilder sql, Map<String, Set<String>> columnAliases, Map<String, VisualizationIntervalColumn> intervals, String queryAlias, IVisualizationSourceQuery joinQuery)
    {
        for (Map.Entry<String, VisualizationIntervalColumn> entry : intervals.entrySet())
        {
            sql.append(", ");
            sql.append(queryAlias);
            sql.append(".");
            sql.append(entry.getValue().getSimpleAlias());
        }

        Container container = joinQuery.getContainer();
        String subjectColumnName = StudyService.get().getSubjectNounSingular(container);

        if (getType() == VisualizationSQLGenerator.ChartType.TIME_VISITBASED)
        {
            sql.append(", ");
            sql.append(queryAlias);
            sql.append(".");
            String columnAlias = columnAliases.get(subjectColumnName + "Visit/sequencenum").iterator().next();
            sql.append(columnAlias);
        }
    }

    @Override
    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery first, IVisualizationSourceQuery second)
    {
        if (!first.getContainer().equals(second.getContainer()))
            throw new IllegalArgumentException("Can't yet join across containers.");

        List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinCols = new ArrayList<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>();

        DataSet.KeyType firstType = StudyService.get().getDatasetKeyType(first.getContainer(), first.getQueryName());
        DataSet.KeyType secondType = StudyService.get().getDatasetKeyType(second.getContainer(), second.getQueryName());
        String firstSubjectColumnName = StudyService.get().getSubjectColumnName(first.getContainer());
        String firstSubjectNounSingular = StudyService.get().getSubjectNounSingular(first.getContainer());
        // allow null results for this column so as to follow the lead of the primary measure column for this query:
        VisualizationSourceColumn firstSubjectCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectColumnName, true);
        String secondSubjectColName = StudyService.get().getSubjectColumnName(second.getContainer());
        String secondSubjectNounSingular = StudyService.get().getSubjectNounSingular(second.getContainer());
        // allow null results for this column so as to follow the lead of the primary measure column for this query:
        VisualizationSourceColumn secondSubjectCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectColName, true);

        joinCols.add(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstSubjectCol, secondSubjectCol));
        // if either dataset is demographic, it's sufficient to join on subject only:
        if (firstType != DataSet.KeyType.SUBJECT && secondType != DataSet.KeyType.SUBJECT)
        {
            // for non-demographic datasets, join on subject/visit, allowing null results for this column so as to follow the lead of the primary measure column for this query:
            VisualizationSourceColumn firstSequenceCol;
            VisualizationSourceColumn secondSequenceCol;
            if (getType() == VisualizationSQLGenerator.ChartType.TIME_VISITBASED)
            {
                firstSequenceCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectNounSingular + "Visit/sequencenum", true);
                secondSequenceCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectNounSingular + "Visit/sequencenum", true);
            }
            else
            {
                firstSequenceCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectNounSingular + "Visit/VisitDate", true);
                secondSequenceCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectNounSingular + "Visit/VisitDate", true);
            }
            joinCols.add(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstSequenceCol, secondSequenceCol));
        }

        return joinCols;
    }

    @Override
    protected boolean isValid(QueryView view, ColumnMatchType type)
    {
        if (type == ColumnMatchType.CONFIGURED_MEASURES)
        {
            TableInfo tinfo = view.getTable();
            return tinfo != null && tinfo.getColumnNameSet().contains("ParticipantSequenceKey");
        }
        else
            return super.isValid(view, type);
    }

    protected Map<ColumnInfo, QueryView> getMatchingColumns(Container container, Collection<QueryView> views, ColumnMatchType type)
    {
        Map<ColumnInfo, QueryView> matches = super.getMatchingColumns(container, views, type);
        if (type == ColumnMatchType.DATETIME_COLS)
        {
            Study study = StudyService.get().getStudy(container);
            // for visit based studies, we will look for the participantVisit.VisitDate column and
            // if found, return that as a date measure
            if (study != null && study.getTimepointType().isVisitBased())
            {
                for (QueryView view : views)
                {
                    TableInfo tinfo = view.getTable();
                    String visitColName = StudyService.get().getSubjectVisitColumnName(container);
                    ColumnInfo visitCol = tinfo.getColumn(visitColName);
                    if (visitCol != null)
                    {
                        TableInfo visitTable = visitCol.getFkTableInfo();
                        if (visitTable != null)
                        {
                            ColumnInfo visitDate = visitTable.getColumn("visitDate");
                            if (visitDate != null)
                            {
                                visitDate.setFieldKey(FieldKey.fromParts(visitColName, visitDate.getName()));
                                matches.put(visitDate, view);
                            }
                        }
                    }
                }
            }
        }
        return matches;
    }

    @Override
    public Map<ColumnInfo, QueryView> getZeroDateMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        // For studies, valid zero date columns are found in demographic datasets only:
        Map<ColumnInfo, QueryView> measures = new HashMap<ColumnInfo, QueryView>();
        Study study = StudyService.get().getStudy(context.getContainer());
        if (study != null)
        {
            for (DataSet ds : study.getDataSets())
            {
                if (ds.isDemographicData())
                {
                    DefaultSchema defSchema = DefaultSchema.get(context.getUser(), context.getContainer());
                    UserSchema schema = (UserSchema)defSchema.getSchema("study");
                    assert schema != null : "Study schema should exist";
                    QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, ds.getName());
                    QueryView view = new QueryView(schema, settings, null);

                    for (DisplayColumn dc : view.getDisplayColumns())
                    {
                        ColumnInfo col = dc.getColumnInfo();
                        if (col != null && ColumnMatchType.DATETIME_COLS.match(col))
                           measures.put(col, view);
                    }
                }
            }
        }
        return measures;
    }

    private static final boolean INCLUDE_DEMOGRAPHIC_DIMENSIONS = false;
    @Override
    public Map<ColumnInfo, QueryView> getDimensions(ViewContext context, String queryName)
    {
        Map<ColumnInfo, QueryView> dimensions = super.getDimensions(context, queryName);
        if (INCLUDE_DEMOGRAPHIC_DIMENSIONS)
        {
            // include dimensions from demographic data sources
            Study study = StudyService.get().getStudy(context.getContainer());
            if (study != null)
            {
                for (DataSet ds : study.getDataSets())
                {
                    if (ds.isDemographicData())
                        dimensions.putAll(super.getDimensions(context, ds.getName()));
                }
            }
        }
        return dimensions;
    }
}
