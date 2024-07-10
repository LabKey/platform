/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
package org.labkey.study.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonForm;
import org.labkey.api.data.Container;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.view.HttpView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a study's cohort/treatment/visit mapping information. Used to serialize JSON to the treatment schedule.
 */
public class StudyTreatmentSchedule implements ApiJsonForm
{
    Container _container;

    // treatment schedule properties
    List<TreatmentImpl> _treatments;
    Collection<VisitImpl> _visits;
    Collection<CohortImpl> _cohorts;

    public StudyTreatmentSchedule()
    {}

    public StudyTreatmentSchedule(Container container)
    {
        _container = container;
    }

    public void setTreatments(List<TreatmentImpl> treatments)
    {
        _treatments = treatments;
    }

    public List<TreatmentImpl> getTreatments()
    {
        return _treatments;
    }

    public List<Map<String, Object>> serializeTreatments()
    {
        List<Map<String, Object>> treatmentList = new ArrayList<>();
        for (TreatmentImpl treatment : _treatments)
        {
            treatmentList.add(treatment.serialize());
        }
        return treatmentList;
    }

    public void setVisits(Collection<VisitImpl> visits)
    {
        _visits = visits;
    }

    public Collection<VisitImpl> getVisits()
    {
        return _visits;
    }

    public List<Map<String, Object>> serializeVisits()
    {
        List<Map<String, Object>> visitList = new ArrayList<>();
        List<Integer> includedIds = getIncludedVisitIds();
        for (VisitImpl v : _visits)
        {
            Map<String, Object> visitProperties = new HashMap<>();
            visitProperties.put("RowId", v.getRowId());
            visitProperties.put("Label", v.getDisplayString());
            visitProperties.put("DisplayOrder", v.getDisplayOrder());
            visitProperties.put("SequenceNumMin", v.getSequenceNumMin());

            // tag those visits that are used in the treatment schedule
            visitProperties.put("Included", includedIds.contains(v.getRowId()));

            visitList.add(visitProperties);
        }
        return visitList;
    }

    private List<Integer> getIncludedVisitIds()
    {
        List<Integer> ids = new ArrayList<>();
        for (CohortImpl cohort : _cohorts)
        {
            for (TreatmentVisitMapImpl tvm : TreatmentManager.getInstance().getStudyTreatmentVisitMap(_container, cohort.getRowId()))
            {
                if (!ids.contains(tvm.getVisitId()))
                    ids.add(tvm.getVisitId());
            }
        }

        return ids;
    }

    public void setCohorts(Collection<CohortImpl> cohorts)
    {
        _cohorts = cohorts;
    }

    public Collection<CohortImpl> getCohorts()
    {
        return _cohorts;
    }

    public List<Map<String, Object>> serializeCohortMapping()
    {
        List<Map<String, Object>> cohortMappingList = new ArrayList<>();
        for (CohortImpl cohort : _cohorts)
        {
            Map<String, Object> mapProperties = new HashMap<>();
            mapProperties.put("RowId", cohort.getRowId());
            mapProperties.put("Label", cohort.getLabel());
            mapProperties.put("SubjectCount", cohort.getSubjectCount());
            mapProperties.put("CanDelete", !cohort.isInUse());

            List<Map<String, Integer>> treatmentVisitMap = new ArrayList<>();
            for (TreatmentVisitMapImpl mapping : TreatmentManager.getInstance().getStudyTreatmentVisitMap(_container, cohort.getRowId()))
            {
                Map<String, Integer> visitMapping = new HashMap<>();
                visitMapping.put("CohortId", mapping.getCohortId());
                visitMapping.put("TreatmentId", mapping.getTreatmentId());
                visitMapping.put("VisitId", mapping.getVisitId());
                treatmentVisitMap.add(visitMapping);
            }
            mapProperties.put("VisitMap", treatmentVisitMap);

            cohortMappingList.add(mapProperties);
        }
        return cohortMappingList;
    }

    @Override
    public void bindJson(JSONObject json)
    {
        _container = HttpView.currentContext().getContainer();

        JSONArray treatmentsJSON = json.optJSONArray("treatments");
        if (treatmentsJSON != null)
        {
            _treatments = new ArrayList<>();
            for (JSONObject treatment : JsonUtil.toJSONObjectList(treatmentsJSON))
                _treatments.add(TreatmentImpl.fromJSON(treatment, _container));
        }

        JSONArray cohortsJSON = json.optJSONArray("cohorts");
        if (cohortsJSON != null)
        {
            _cohorts = new ArrayList<>();
            for (JSONObject cohortJSON : JsonUtil.toJSONObjectList(cohortsJSON))
                _cohorts.add(CohortImpl.fromJSON(cohortJSON));
        }
    }
}
