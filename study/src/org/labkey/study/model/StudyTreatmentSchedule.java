/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Container;
import org.labkey.api.view.HttpView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a study's cohort/treatment/visit mapping information. Used to serialize JSON to the treatment schedule.
 */
public class StudyTreatmentSchedule implements CustomApiForm
{
    Container _container;

    // treatment schedule properties
    List<TreatmentImpl> _treatments;
    List<VisitImpl> _visits;
    List<CohortImpl> _cohorts;

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

    public void setVisits(List<VisitImpl> visits)
    {
        _visits = visits;
    }

    public List<VisitImpl> getVisits()
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

    public void setCohorts(List<CohortImpl> cohorts)
    {
        _cohorts = cohorts;
    }

    public List<CohortImpl> getCohorts()
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
    public void bindProperties(Map<String, Object> props)
    {
        _container = HttpView.currentContext().getContainer();

        Object treatmentsInfo = props.get("treatments");
        if (treatmentsInfo != null && treatmentsInfo instanceof JSONArray)
        {
            _treatments = new ArrayList<>();

            JSONArray treatmentsJSON = (JSONArray) treatmentsInfo;
            for (int i = 0; i < treatmentsJSON.length(); i++)
                _treatments.add(TreatmentImpl.fromJSON(treatmentsJSON.getJSONObject(i), _container));
        }

        Object cohortsInfo = props.get("cohorts");
        if (cohortsInfo != null && cohortsInfo instanceof JSONArray)
        {
            _cohorts = new ArrayList<>();

            JSONArray cohortsJSON = (JSONArray) cohortsInfo;
            for (int i = 0; i < cohortsJSON.length(); i++)
            {
                JSONObject cohortJSON = cohortsJSON.getJSONObject(i);
                _cohorts.add(CohortImpl.fromJSON(cohortJSON));
            }
        }
    }
}
