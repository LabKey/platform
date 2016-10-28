/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.study.Cohort;
import org.labkey.api.exp.Lsid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Created: Jan 15, 2008 4:27:38 PM
 */
public class CohortImpl extends ExtensibleStudyEntity<CohortImpl> implements Cohort
{
    private static final String DOMAIN_URI_PREFIX = "Cohort";
    public static final DomainInfo DOMAIN_INFO = new StudyDomainInfo(DOMAIN_URI_PREFIX, false);

    private int _rowId = 0;
    private String _lsid;
    private boolean _enrolled = true;
    private Integer _subjectCount;
    private String _description;
    List<TreatmentVisitMapImpl> _treatmentVisitMap;

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        verifyMutability();
        _rowId = rowId;
    }

    public boolean isEnrolled()
    {
        return _enrolled;
    }

    public void setEnrolled(boolean enrolled)
    {
        verifyMutability();
        _enrolled = enrolled;
    }

    @Override
    public String getDomainURIPrefix()
    {
        return DOMAIN_URI_PREFIX;
    }

    @Override
    protected boolean getUseSharedProjectDomain()
    {
        return false;
    }

    public void initLsid()
    {
        Lsid lsid = new Lsid(getDomainURIPrefix(), "Folder-" + getContainer().getRowId(), String.valueOf(getRowId()));
        setLsid(lsid.toString());
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        verifyMutability();
        _lsid = lsid;
    }

    public boolean isInUse()
    {
        return StudyManager.getInstance().isCohortInUse(this);
    }

    public Set<String> getParticipantSet()
    {
        Participant[] participants = CohortManager.getInstance().getParticipantsForCohort(getContainer(), _rowId);
        Set<String> ptids = new HashSet<>();
        for (Participant p : participants)
        {
            ptids.add(p.getParticipantId());
        }
        return ptids;
    }

    public Integer getSubjectCount()
    {
        return _subjectCount;
    }

    public void setSubjectCount(Integer subjectCount)
    {
        verifyMutability();
        _subjectCount = subjectCount;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        verifyMutability();
        _description = description;
    }

    public void setTreatmentVisitMap(List<TreatmentVisitMapImpl> treatmentVisitMap)
    {
        _treatmentVisitMap = treatmentVisitMap;
    }

    public List<TreatmentVisitMapImpl> getTreatmentVisitMap()
    {
        return _treatmentVisitMap;
    }

    public static CohortImpl fromJSON(@NotNull JSONObject o)
    {
        CohortImpl cohort = new CohortImpl();
        cohort.setLabel(o.getString("Label"));
        if (o.containsKey("SubjectCount") && !"".equals(o.getString("SubjectCount")))
            cohort.setSubjectCount(o.getInt("SubjectCount"));
        if (o.containsKey("RowId"))
            cohort.setRowId(o.getInt("RowId"));

        Object visitMapInfo = o.get("VisitMap");
        if (visitMapInfo != null && visitMapInfo instanceof JSONArray)
        {
            JSONArray visitMapJSON = (JSONArray) visitMapInfo;

            List<TreatmentVisitMapImpl> treatmentVisitMap = new ArrayList<>();
            for (int j = 0; j < visitMapJSON.length(); j++)
                treatmentVisitMap.add(TreatmentVisitMapImpl.fromJSON(visitMapJSON.getJSONObject(j)));

            cohort.setTreatmentVisitMap(treatmentVisitMap);
        }

        return cohort;
    }
}
