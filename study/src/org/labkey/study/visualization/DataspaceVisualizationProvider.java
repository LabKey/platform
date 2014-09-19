/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.VisualizationSourceColumn;
import org.labkey.study.query.StudyQuerySchema;

import java.util.List;
import java.util.Map;

/**
 * Created by cnathe on 9/9/14.
 */
public class DataspaceVisualizationProvider extends StudyVisualizationProvider
{
    public DataspaceVisualizationProvider(StudyQuerySchema schema)
    {
        super(schema);
    }

    @Override
    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery first, IVisualizationSourceQuery second, boolean isGroupByQuery)
    {
        List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinCols = super.getJoinColumns(factory, first, second, isGroupByQuery);

        // prior to Dataspace we were always gettting data from a single study container, now we
        // need to include the container in the where clause to make sure
        Study firstStudy = StudyService.get().getStudy(first.getContainer());
        if (firstStudy != null && firstStudy.isDataspaceStudy())
        {
            VisualizationSourceColumn firstContainerCol = factory.create(first.getSchema(), first.getQueryName(), "Container", true);
            VisualizationSourceColumn secondContainerCol = factory.create(second.getSchema(), second.getQueryName(), "Container", true);
            joinCols.add(new Pair<>(firstContainerCol, secondContainerCol));
        }

        return joinCols;
    }

    @Override
    protected VisualizationSourceColumn getVisitJoinColumn(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery query, String subjectNounSingular)
    {
        // issue 20689 : always join by visit sequencenum for Dataspace
        String subjectVisit = subjectNounSingular + "Visit";
        String colName = (query.getQueryName().equalsIgnoreCase(subjectVisit) ? "" : subjectVisit + "/") + "sequencenum";
        return factory.create(query.getSchema(), query.getQueryName(), colName, true);
    }

    @Override
    public String getAlternateJoinOperator(Container container, IVisualizationSourceQuery query)
    {
        // issue 20526: use left join for Dataspace when adding color variable to plot (see Scatter.js use of allowNullResults)
        Study study = StudyService.get().getStudy(container);
        if (study != null && study.isDataspaceStudy() && !query.getQueryName().contains("VisualizationVisitTag"))
            return "LEFT JOIN";
        else
            return super.getAlternateJoinOperator(container, query);
    }

    @Override
    public void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery query)
    {
        // noop - not needed for Dataspace studies
    }

    @Override
    public void addExtraResponseProperties(Map<String, Object> extraProperties)
    {
        // noop - not needed for Dataspace studies
    }
}
