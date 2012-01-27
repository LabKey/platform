/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.VisitDataSet;
import org.labkey.study.model.VisitImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 12, 2012
 * Time: 10:31:17 AM
 */

/**
 * Represents a study's datasets and corresponding timepoints. Used to serialize JSON to the study schedule
 * UI.
 */
public class StudySchedule
{
    VisitImpl[] _visits;
    DataSetDefinition[] _datasets;

    public void setDatasets(DataSetDefinition[] datasets)
    {
        _datasets = datasets;
    }

    public void setVisits(VisitImpl[] visits)
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

    private JSONArray serializeVisits(User user, VisitImpl[] visits)
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

        o.put("name", ColumnInfo.legalNameFromName(visit.getLabel()));
        o.put("label", visit.getLabel());
        o.put("sequenceMin", visit.getSequenceNumMin());
        o.put("sequenceMax", visit.getSequenceNumMax());
        o.put("id", visit.getRowId());
        o.put("showByDefault", visit.isShowByDefault());
        if (visit.getCohort() != null)
            o.put("cohort", serializeCohort(user, (CohortImpl)visit.getCohort()));

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

    private JSONObject serializeDataset(User user, DataSetDefinition ds)
    {
        JSONObject o = new JSONObject();

        o.put("label", ds.getLabel());
        o.put("category", ds.getCategory());
        o.put("description", ds.getDescription());
        o.put("displayOrder", ds.getDisplayOrder());
        o.put("id", ds.getDataSetId());

        return o;
    }

    private JSONArray serializeData(User user, DataSetDefinition[] datasets, VisitImpl[] visits)
    {
        JSONArray d = new JSONArray();
        Map<Integer, VisitImpl> visitMap = new HashMap<Integer, VisitImpl>();

        for (VisitImpl visit : visits)
            visitMap.put(visit.getRowId(), visit);

        for (DataSetDefinition ds : datasets)
        {
            JSONObject o = new JSONObject();

            o.put("dataset", serializeDataset(user, ds));

            for (VisitDataSet vds : ds.getVisitDataSets())
            {
                VisitImpl visit = visitMap.get(vds.getVisitRowId());
                if (visit != null)
                    o.put(ColumnInfo.legalNameFromName(visit.getLabel()), serializeVisit(user, visit, vds.isRequired()));
            }
            d.put(o);
        }
        return d;
    }
}
