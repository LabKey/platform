/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.study.SampleManager;

import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:32 AM
 */
public class Study extends AbstractStudyEntity<Study>
{
    private String _label;
    private boolean _dateBased;
    private Date _startDate;
    private boolean _studySecurity;
    private String _participantCohortProperty;
    private Integer _participantCohortDataSetId;
    private boolean _datasetRowsEditable;

    public Study()
    {
    }

    public Study(Container container, String label)
    {
        super(container);
        _label = label;
        _entityId = GUID.makeGUID();
    }

    
    public String getLabel()
    {
        return _label;
    }


    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }


    public Visit[] getVisits()
    {
        return StudyManager.getInstance().getVisits(this);
    }


    public synchronized DataSetDefinition getDataSet(int id)
    {
        return StudyManager.getInstance().getDataSetDefinition(this, id);
    }


    public synchronized DataSetDefinition[] getDataSets()
    {
        return StudyManager.getInstance().getDataSetDefinitions(this);
    }


    public synchronized SampleRequestActor[] getSampleRequestActors() throws SQLException
    {
        return SampleManager.getInstance().getRequirementsProvider().getActors(getContainer());
    }

    public synchronized Set<Integer> getSampleRequestActorsInUse() throws SQLException
    {
        Collection<SampleRequestActor> actors = SampleManager.getInstance().getRequirementsProvider().getActorsInUse(getContainer());
        Set<Integer> ids = new HashSet<Integer>();
        for (SampleRequestActor actor : actors)
            ids.add(actor.getRowId());
        return ids;
    }

    public synchronized Site[] getSites()
    {
        return StudyManager.getInstance().getSites(getContainer());
    }

    public synchronized Cohort[] getCohorts(User user)
    {
        return StudyManager.getInstance().getCohorts(getContainer(), user);
    }

    public synchronized SampleRequestStatus[] getSampleRequestStatuses(User user) throws SQLException
    {
        return SampleManager.getInstance().getRequestStatuses(getContainer(), user);
    }

    public synchronized Set<Integer> getSampleRequestStatusesInUse() throws SQLException
    {
        return SampleManager.getInstance().getRequestStatusIdsInUse(getContainer());
    }

    public synchronized SampleManager.RepositorySettings getRepositorySettings() throws SQLException
    {
        return SampleManager.getInstance().getRepositorySettings(getContainer());
    }
    
    public Object getPrimaryKey()
    {
        return getContainer();
    }

    public int getRowId()
    {
        return -1;
    }

    public void updateACL(ACL acl)
    {
        super.updateACL(acl);
        StudyManager.getInstance().scrubDatasetAcls(this);
    }

    @Override
    protected boolean supportsACLUpdate()
    {
        return true;
    }

    public boolean isDateBased()
    {
        return _dateBased;
    }

    public void setDateBased(boolean dateBased)
    {
        verifyMutability();
        _dateBased = dateBased;
    }

    public boolean isStudySecurity()
    {
        return _studySecurity;
    }

    public void setStudySecurity(boolean studySecurity)
    {
        verifyMutability();
        _studySecurity = studySecurity;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        verifyMutability();
        _startDate = startDate;
    }

    public boolean isDatasetRowsEditable()
    {
        return _datasetRowsEditable;
    }

    public void setDatasetRowsEditable(boolean datasetRowsEditable)
    {
        _datasetRowsEditable = datasetRowsEditable;
    }

    public String getParticipantCohortProperty()
    {
        return _participantCohortProperty;
    }

    public void setParticipantCohortProperty(String participantCohortProperty)
    {
        _participantCohortProperty = participantCohortProperty;
    }

    public Integer getParticipantCohortDataSetId()
    {
        return _participantCohortDataSetId;
    }

    public void setParticipantCohortDataSetId(Integer participantCohortDataSetId)
    {
        _participantCohortDataSetId = participantCohortDataSetId;
    }

    public static class SummaryStatistics
    {
        private int datasetCount;
        private SortedMap<String, Integer> datasetCategoryCounts;
        private int visitCount;
        private int siteCount;
        private int participantCount;
        private int sampleCount;

        public int getDatasetCount()
        {
            return datasetCount;
        }

        public void setDatasetCount(int datasetCount)
        {
            this.datasetCount = datasetCount;
        }

        public SortedMap<String, Integer> getDatasetCategoryCounts()
        {
            return datasetCategoryCounts;
        }

        public void setDatasetCategoryCounts(SortedMap<String, Integer> datasetCategoryCounts)
        {
            this.datasetCategoryCounts = datasetCategoryCounts;
        }

        public int getVisitCount()
        {
            return visitCount;
        }

        public void setVisitCount(int visitCount)
        {
            this.visitCount = visitCount;
        }

        public int getSiteCount()
        {
            return siteCount;
        }

        public void setSiteCount(int siteCount)
        {
            this.siteCount = siteCount;
        }

        public int getParticipantCount()
        {
            return participantCount;
        }

        public void setParticipantCount(int participantCount)
        {
            this.participantCount = participantCount;
        }

        public int getSampleCount()
        {
            return sampleCount;
        }

        public void setSampleCount(int sampleCount)
        {
            this.sampleCount = sampleCount;
        }
    }
}
