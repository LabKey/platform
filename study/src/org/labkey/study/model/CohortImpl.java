/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.labkey.api.exp.Lsid;
import org.labkey.api.study.Cohort;
import org.labkey.api.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

    @Override
    public Object getPrimaryKey()
    {
        return getRowId();
    }

    @Override
    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        verifyMutability();
        _rowId = rowId;
    }

    @Override
    public boolean isEnrolled()
    {
        return _enrolled;
    }

    @Override
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

    @Override
    public void initLsid()
    {
        Lsid lsid = new Lsid(getDomainURIPrefix(), "Folder-" + getContainer().getRowId(), String.valueOf(getRowId()));
        setLsid(lsid.toString());
    }

    @Override
    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        verifyMutability();
        _lsid = lsid;
    }

    @Override
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

    @Override
    public Integer getSubjectCount()
    {
        return _subjectCount;
    }

    @Override
    public void setSubjectCount(Integer subjectCount)
    {
        verifyMutability();
        _subjectCount = subjectCount;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
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
        if (o.has("SubjectCount") && !"".equals(o.getString("SubjectCount")))
            cohort.setSubjectCount(o.getInt("SubjectCount"));
        if (o.has("RowId"))
            cohort.setRowId(o.getInt("RowId"));

        JSONArray visitMapJSON = o.optJSONArray("VisitMap");
        if (visitMapJSON != null)
        {
            List<TreatmentVisitMapImpl> treatmentVisitMap = new ArrayList<>();
            for (JSONObject json : JsonUtil.toJSONObjectList(visitMapJSON))
                treatmentVisitMap.add(TreatmentVisitMapImpl.fromJSON(json));

            cohort.setTreatmentVisitMap(treatmentVisitMap);
        }

        return cohort;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CohortImpl cohort = (CohortImpl) o;
        return _rowId == cohort._rowId && _enrolled == cohort._enrolled && Objects.equals(_lsid, cohort._lsid) && Objects.equals(_subjectCount, cohort._subjectCount) && Objects.equals(_description, cohort._description);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_rowId, _lsid, _enrolled, _subjectCount, _description);
    }
}
