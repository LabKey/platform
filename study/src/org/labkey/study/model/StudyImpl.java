/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.util.GUID;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.study.Study;
import org.labkey.study.SampleManager;
import org.labkey.study.query.StudyQuerySchema;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:32 AM
 */
public class StudyImpl extends ExtensibleStudyEntity<StudyImpl> implements Study
{
    private String _label;
    private boolean _dateBased;
    private Date _startDate;
    private SecurityType _securityType = SecurityType.BASIC_READ; // Default value. Not allowed to be null
    private String _participantCohortProperty;
    private Integer _participantCohortDataSetId;
    private boolean _manualCohortAssignment;
    private String _lsid;
    private Integer _defaultPipelineQCState;
    private Integer _defaultAssayQCState;
    private Integer _defaultDirectEntryQCState;
    private boolean _showPrivateDataByDefault;

    public StudyImpl()
    {
    }

    public StudyImpl(Container container, String label)
    {
        super(container);
        _label = label;
        _entityId = GUID.makeGUID();
    }

    @Override
    public SecurableResource getParentResource()
    {
        //overriden to return the container
        //all other study entities return the study,
        //but the study's parent is the container
        return getContainer();
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        List<SecurableResource> ret = new ArrayList<SecurableResource>();

        //add all datasets the user has admin perms on
        for(DataSetDefinition dsDef : getDataSets())
        {
            SecurityPolicy policy = SecurityManager.getPolicy(dsDef);
//            if(policy.hasPermission(user, AdminPermission.class))
                ret.add(dsDef);
        }

        return ret;
    }

    @NotNull
    @Override
    public String getResourceDescription()
    {
        return "The study " + _label;
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


    public VisitImpl[] getVisits()
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

    public synchronized SiteImpl[] getSites()
    {
        return StudyManager.getInstance().getSites(getContainer());
    }

    public synchronized CohortImpl[] getCohorts(User user)
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

    @Override
    public void savePolicy(SecurityPolicy policy)
    {
        super.savePolicy(policy);
        StudyManager.getInstance().scrubDatasetAcls(this, policy);
    }

    @Override
    protected boolean supportsPolicyUpdate()
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

    public SecurityType getSecurityType()
    {
        return _securityType;
    }

    public void setSecurityType(SecurityType securityType)
    {
        verifyMutability();
        if (securityType == null)
            throw new IllegalArgumentException("securityType cannot be null");
        _securityType = securityType;
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

    public boolean isManualCohortAssignment()
    {
        return _manualCohortAssignment;
    }

    public void setManualCohortAssignment(boolean manualCohortAssignment)
    {
        _manualCohortAssignment = manualCohortAssignment;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        verifyMutability();
        this._lsid = lsid;
    }

    public Integer getDefaultPipelineQCState()
    {
        return _defaultPipelineQCState;
    }

    public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
    {
        _defaultPipelineQCState = defaultPipelineQCState;
    }

    public Integer getDefaultAssayQCState()
    {
        return _defaultAssayQCState;
    }

    public void setDefaultAssayQCState(Integer defaultAssayQCState)
    {
        _defaultAssayQCState = defaultAssayQCState;
    }

    public Integer getDefaultDirectEntryQCState()
    {
        return _defaultDirectEntryQCState;
    }

    public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
    {
        _defaultDirectEntryQCState = defaultDirectEntryQCState;
    }

    public boolean isShowPrivateDataByDefault()
    {
        return _showPrivateDataByDefault;
    }

    public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
    {
        _showPrivateDataByDefault = showPrivateDataByDefault;
    }

    public int getNumExtendedProperties(User user)
    {
        StudyQuerySchema schema = new StudyQuerySchema(this, user, true);
        String domainURI = StudyManager.getInstance().getDomainURI(schema.getContainer(), StudyImpl.class);
        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);

        if (domain == null)
            return 0;

        return domain.getProperties().length;
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
