/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.study.reports;

import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.view.HttpView;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import java.util.HashMap;
import java.util.List;

/**
 * User: Matthew
 * Date: Feb 1, 2006
 * Time: 10:01:53 AM
 * <p/>
 * Useful helpers for any study view
 */
public class BaseStudyView<T> extends HttpView<T>
{
    private final Study _study;
    private final StudyManager _studyManager;
    private final HashMap<Integer, VisitImpl> _visitMap = new HashMap<>();
    private final HashMap<Integer, DatasetDefinition> _datasetMap = new HashMap<>();

    private List<VisitImpl> _visits;            // display ordered
    private List<DatasetDefinition> _datasetDefs;

    public BaseStudyView(Study study)
    {
        _study = study;
        _studyManager = StudyManager.getInstance();
    }

    protected List<VisitImpl> getVisits()
    {
        if (null == _visits)
        {
            _visits = _studyManager.getVisits(_study, Visit.Order.DISPLAY);
            for (VisitImpl v : _visits)
                _visitMap.put(v.getRowId(), v);
        }
        return _visits;
    }

    protected VisitImpl getVisit(int v)
    {
        getVisits();
        return _visitMap.get(v);
    }

    protected List<DatasetDefinition> getDatasets()
    {
        if (null == _datasetDefs)
        {
            _datasetDefs = _studyManager.getDatasetDefinitions(_study);
            for (DatasetDefinition d : _datasetDefs)
                _datasetMap.put(d.getDatasetId(), d);
        }
        return _datasetDefs;
    }

    protected Dataset getDatasetDefinition(int d)
    {
        getDatasets();
        return _datasetMap.get(d);
    }
}