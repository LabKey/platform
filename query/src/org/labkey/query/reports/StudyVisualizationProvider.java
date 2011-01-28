package org.labkey.query.reports;

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

import java.util.*;

/**
* Copyright (c) 2008-2010 LabKey Corporation
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
    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinColumns(VisualizationSourceQuery first, VisualizationSourceQuery second)
    {
        if (!first.getContainer().equals(second.getContainer()))
            throw new IllegalArgumentException("Can't yet join across containers.");

        DataSet.KeyType firstType = StudyService.get().getDatasetKeyType(first.getContainer(), first.getQueryName());
        DataSet.KeyType secondType = StudyService.get().getDatasetKeyType(second.getContainer(), second.getQueryName());
        if (firstType == DataSet.KeyType.SUBJECT || secondType == DataSet.KeyType.SUBJECT)
        {
            // if either dataset is demographic, it's sufficient to join on subject only:
            String firstSubjectCol = StudyService.get().getSubjectColumnName(first.getContainer());
            VisualizationSourceColumn firstSourceCol = new VisualizationSourceColumn(first.getSchema(), first.getQueryName(), firstSubjectCol);
            String secondSubjectCol = StudyService.get().getSubjectColumnName(second.getContainer());
            VisualizationSourceColumn secondSourceCol = new VisualizationSourceColumn(second.getSchema(), second.getQueryName(), secondSubjectCol);
            return Collections.singletonList(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstSourceCol, secondSourceCol));
        }
        else
        {
            // for non-demographic datasets, join on subject/visit:
            VisualizationSourceColumn firstSourceCol = new VisualizationSourceColumn(first.getSchema(), first.getQueryName(), "ParticipantSequenceKey");
            VisualizationSourceColumn secondSourceCol = new VisualizationSourceColumn(second.getSchema(), second.getQueryName(), "ParticipantSequenceKey");
            return Collections.singletonList(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstSourceCol, secondSourceCol));
        }
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
