/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.study.designer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.VisitDataset;
import org.labkey.study.model.VisitImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Jan 12, 2012
 * Time: 10:31:17 AM
 */

/**
 * Represents a study's datasets and corresponding timepoints. Used to serialize JSON to the study schedule
 * UI.
 */
public class StudySchedule implements CustomApiForm
{
    List<VisitImpl> _visits;
    List<DatasetDefinition> _datasets;
    Map<String, DataViewInfo> _viewInfo = new HashMap<>();
    Map<Integer, List<VisitDataset>> _schedule;

    public void setDatasets(List<DatasetDefinition> datasets, List<DataViewInfo> views)
    {
        _datasets = datasets;

        for (DataViewInfo info : views)
        {
            _viewInfo.put(info.getId(), info);
        }
    }

    public void setVisits(List<VisitImpl> visits)
    {
        _visits = visits;
    }

    public JSONObject toJSON(User user)
    {
        JSONObject o = new JSONObject();

        if (_visits != null)
            o.put("timepoints", serializeVisits(user, _visits));
        if (_datasets != null & _visits != null)
            o.put("data", serializeData(user, _datasets, _visits));

        return o;
    }

    private JSONArray serializeVisits(User user, List<VisitImpl> visits)
    {
        JSONArray v = new JSONArray();

        for (VisitImpl visit : visits)
        {
            v.put(serializeVisit(user, visit, false));
        }
        return v;
    }

    private JSONObject serializeVisit(User user, VisitImpl visit, boolean required)
    {
        JSONObject o = new JSONObject();

        o.put("label", visit.getDisplayString());
        o.put("sequenceMin", visit.getSequenceNumMin());
        o.put("sequenceMax", visit.getSequenceNumMax());
        o.put("id", visit.getRowId());
        o.put("showByDefault", visit.isShowByDefault());
        if (visit.getCohort() != null)
            o.put("cohort", serializeCohort(user, (CohortImpl) visit.getCohort()));

        if (required)
            o.put("required", true);

        return o;
    }

    private JSONObject serializeCohort(User user, CohortImpl cohort)
    {
        JSONObject o = new JSONObject();

        o.put("label", cohort.getLabel());
        o.put("id", cohort.getRowId());

        return o;
    }

    private JSONObject serializeDataset(User user, DatasetDefinition ds)
    {
        JSONObject o = new JSONObject();

        // merge dataset info with data view info to pick up tag information
        if (_viewInfo.containsKey(ds.getEntityId()))
        {
            DataViewInfo info = _viewInfo.get(ds.getEntityId());

            o = DataViewService.get().toJSON(ds.getContainer(), user, info);
            o.put("entityId", ds.getEntityId());
        }
        o.put("label", ds.getLabel());

        ViewCategory vc = null;
        if (ds.getCategoryId() != null)
            vc = ViewCategoryManager.getInstance().getCategory(ds.getContainer(), ds.getCategoryId());

        if (vc != null)
            o.put("category", vc.toJSON(user));

        o.put("description", ds.getDescription());
        o.put("displayOrder", ds.getDisplayOrder());
        o.put("id", ds.getDatasetId());
        o.put("type", ds.getType());

        return o;
    }

    private JSONArray serializeData(User user, List<DatasetDefinition> datasets, List<VisitImpl> visits)
    {
        JSONArray d = new JSONArray();
        Map<Integer, VisitImpl> visitMap = new HashMap<>();

        for (VisitImpl visit : visits)
            visitMap.put(visit.getRowId(), visit);

        for (DatasetDefinition ds : datasets)
        {
            JSONObject o = new JSONObject();

            o.put("dataset", serializeDataset(user, ds));

            for (VisitDataset vds : ds.getVisitDatasets())
            {
                VisitImpl visit = visitMap.get(vds.getVisitRowId());
                if (visit != null)
                    o.put(String.valueOf(visit.getRowId()), serializeVisit(user, visit, vds.isRequired()));
            }
            d.put(o);
        }
        return d;
    }

    @Override
    public void bindProperties(Map<String, Object> props)
    {
        Object schedule = props.get("schedule");
        _schedule = new LinkedHashMap<>();
        Container container = HttpView.currentContext().getContainer();

        if (schedule instanceof JSONArray)
        {
            JSONArray schedules = (JSONArray) schedule;
            for (int i = 0; i < schedules.length(); i++)
            {
                JSONObject rec = schedules.getJSONObject(i);
                List<VisitDataset> timepoints = new ArrayList<>();
                Integer datasetId = null;

                for (Map.Entry<String, Object> entry : rec.entrySet())
                {
                    if ("id".equals(entry.getKey()))
                    {
                        JSONObject id = (JSONObject) entry.getValue();
                        datasetId = id.getInt("id");
                    }
                    else if (entry.getValue() instanceof JSONObject)
                    {
                        JSONObject timepoint = (JSONObject) entry.getValue();

                        Integer id = (Integer) timepoint.get("id");
                        Boolean required = (Boolean) timepoint.get("required");

                        if (id != null)
                        {
                            timepoints.add(new VisitDataset(container, -1, id, required != null ? required : false));
                        }
                    }
                }

                if (datasetId != null)
                    _schedule.put(datasetId, timepoints);
            }
        }
    }

    public Map<Integer, List<VisitDataset>> getSchedule()
    {
        return _schedule;
    }
}
