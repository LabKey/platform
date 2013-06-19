/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.study.Cohort;
import org.labkey.api.exp.Lsid;

import java.util.HashSet;
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
}
