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
 * User: cnathe
 * Date: 1/14/14
 */

/**
 * Represents a study's cohort/treatment/visit mapping information. Used to serialize JSON to the immunization schedule.
 */
public class StudyImmunizationSchedule implements CustomApiForm
{
    Container _container;
    Integer _cohortRowId;
    String _cohortLabel;
    Integer _cohortSubjectCount;
    Map<Integer, Integer> _treatmentVisitMap = new HashMap<>(); // map visitId -> treatmentId

    // immunization schedule properties
    List<TreatmentImpl> _treatments;
    List<VisitImpl> _visits;
    List<CohortImpl> _cohorts;

    public StudyImmunizationSchedule()
    {}

    public StudyImmunizationSchedule(Container container)
    {
        _container = container;
    }

    public void setTreatments(List<TreatmentImpl> treatments)
    {
        _treatments = treatments;
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

    public List<Map<String, Object>> serializeVisits()
    {
        List<Map<String, Object>> visitList = new ArrayList<>();
        List<Integer> includedIds = getIncludedVisitIds();
        for (VisitImpl v : _visits)
        {
            Map<String, Object> visitProperties = new HashMap<>();
            visitProperties.put("RowId", v.getRowId());
            visitProperties.put("Label", v.getDisplayString());
            visitProperties.put("SortOrder", v.getDisplayOrder());

            // tag those visits that are used in the immunization schedule
            visitProperties.put("Included", includedIds.contains(v.getRowId()));

            visitList.add(visitProperties);
        }
        return visitList;
    }

    public void setCohorts(List<CohortImpl> cohorts)
    {
        _cohorts = cohorts;
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

            List<TreatmentVisitMapImpl> treatmentVisitMap = TreatmentManager.getInstance().getStudyTreatmentVisitMap(_container, cohort.getRowId());
            for (TreatmentVisitMapImpl tvm : treatmentVisitMap)
                mapProperties.put("Visit"+tvm.getVisitId(), tvm.getTreatmentId());

            cohortMappingList.add(mapProperties);
        }
        return cohortMappingList;
    }

    private List<Integer> getIncludedVisitIds()
    {
        List<Integer> ids = new ArrayList<>();
        for (CohortImpl cohort : _cohorts)
        {
            List<TreatmentVisitMapImpl> treatmentVisitMap = TreatmentManager.getInstance().getStudyTreatmentVisitMap(_container, cohort.getRowId());
            for (TreatmentVisitMapImpl tvm : treatmentVisitMap)
            {
                if (!ids.contains(tvm.getVisitId()))
                    ids.add(tvm.getVisitId());
            }
        }
        return ids;
    }

    public Integer getCohortRowId()
    {
        return _cohortRowId;
    }

    public String getCohortLabel()
    {
        return _cohortLabel;
    }

    public Integer getCohortSubjectCount()
    {
        return _cohortSubjectCount;
    }

    public Map<Integer, Integer> getTreatmentVisitMap()
    {
        return _treatmentVisitMap;
    }

    @Override
    public void bindProperties(Map<String, Object> props)
    {
        _container = HttpView.currentContext().getContainer();

        Object cohortInfo = props.get("cohort");
        if (cohortInfo != null && cohortInfo instanceof JSONObject)
        {
            JSONObject cohortJSON = (JSONObject) cohortInfo;
            _cohortRowId = (Integer) cohortJSON.get("rowId");
            _cohortLabel = (String) cohortJSON.get("label");
            _cohortSubjectCount = (Integer) cohortJSON.get("subjectCount");
        }

        Object treatmentVisitMappingInfo = props.get("treatmentVisitMapping");
        if (treatmentVisitMappingInfo != null && treatmentVisitMappingInfo instanceof JSONArray)
        {
            JSONArray treatmentVisitMapping = (JSONArray) treatmentVisitMappingInfo;
            for (int i = 0; i < treatmentVisitMapping.length(); i++)
            {
                JSONObject mapping = treatmentVisitMapping.getJSONObject(i);
                _treatmentVisitMap.put(mapping.getInt("visitId"), mapping.getInt("treatmentId"));
            }
        }
    }
}
