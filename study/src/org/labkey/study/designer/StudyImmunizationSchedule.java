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
package org.labkey.study.designer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Container;
import org.labkey.api.view.HttpView;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.TreatmentImpl;
import org.labkey.study.model.TreatmentVisitMapImpl;
import org.labkey.study.model.VisitImpl;

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

    public List<JSONObject> serializeTreatments()
    {
        List<JSONObject> json = new ArrayList<>();
        for (TreatmentImpl treatment : _treatments)
        {
            JSONObject treatmentJSON = new JSONObject();
            treatmentJSON.put("RowId", treatment.getRowId());
            treatmentJSON.put("Label", treatment.getLabel());
            json.add(treatmentJSON);
        }
        return json;
    }

    public void setVisits(List<VisitImpl> visits)
    {
        _visits = visits;
    }

    public List<JSONObject> serializeVisits()
    {
        List<JSONObject> json = new ArrayList<>();
        List<Integer> includedIds = getIncludedVisitIds();
        for (VisitImpl v : _visits)
        {
            JSONObject visitJSON = new JSONObject();
            visitJSON.put("RowId", v.getRowId());
            visitJSON.put("Label", v.getDisplayString());
            visitJSON.put("SortOrder", v.getDisplayOrder());

            // tag those visits that are used in the immunization schedule
            visitJSON.put("Included", includedIds.contains(v.getRowId()));

            json.add(visitJSON);
        }
        return json;
    }

    public void setCohorts(List<CohortImpl> cohorts)
    {
        _cohorts = cohorts;
    }

    public List<JSONObject> serializeCohortMapping()
    {
        List<JSONObject> json = new ArrayList<>();
        for (CohortImpl cohort : _cohorts)
        {
            JSONObject mapJSON = new JSONObject();
            mapJSON.put("RowId", cohort.getRowId());
            mapJSON.put("Label", cohort.getLabel());
            mapJSON.put("SubjectCount", cohort.getSubjectCount());

            List<TreatmentVisitMapImpl> treatmentVisitMap = StudyManager.getInstance().getStudyTreatmentVisitMap(_container, cohort.getRowId());
            for (TreatmentVisitMapImpl tvm : treatmentVisitMap)
                mapJSON.put("Visit"+tvm.getVisitId(), tvm.getTreatmentId());

            json.add(mapJSON);
        }
        return json;
    }

    private List<Integer> getIncludedVisitIds()
    {
        List<Integer> ids = new ArrayList<>();
        for (CohortImpl cohort : _cohorts)
        {
            List<TreatmentVisitMapImpl> treatmentVisitMap = StudyManager.getInstance().getStudyTreatmentVisitMap(_container, cohort.getRowId());
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
