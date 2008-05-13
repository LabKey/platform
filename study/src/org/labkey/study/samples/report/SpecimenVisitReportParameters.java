package org.labkey.study.samples.report;

import org.labkey.api.view.ViewForm;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.model.*;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.SampleManager;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Set;
import java.sql.SQLException;

/**
 * Copyright (c) 2008 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* User: brittp
* Created: Jan 14, 2008 12:26:17 PM
*/
public abstract class SpecimenVisitReportParameters extends ViewForm
{
    public enum PARAMS
    {
        statusFilterName,
        viewVialCount,
        viewParticipantCount,
        viewVolume,
        viewPtidList,
        typeLevel
    }

    public enum Status
    {
        ALL("Available and Unavailable Vials"),
        AVAILABLE("Only Available Vials"),
        UNAVAILABLE("Only Unavailable Vials");

        private String _caption;
        public String getCaption()
        {
            return _caption;
        }

        Status(String caption)
        {
            _caption = caption;
        }
    }

    private Integer _cohortId;
    private Cohort _cohort;
    private Status _statusFilter = Status.ALL;
    private boolean _viewVialCount = false;
    private boolean _viewParticipantCount = false;
    private boolean _viewVolume = false;
    private boolean _viewPtidList = false;
    private List<? extends SpecimenVisitReport> _reports;
    private SampleManager.SpecimenTypeLevel _typeLevel = SampleManager.SpecimenTypeLevel.Derivative;

    public String getTypeLevel()
    {
        return _typeLevel.name();
    }

    public void setTypeLevel(String typeLevel)
    {
        _typeLevel = SampleManager.SpecimenTypeLevel.valueOf(typeLevel);
    }

    public SampleManager.SpecimenTypeLevel getTypeLevelEnum()
    {
        return _typeLevel;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
            _cohortId = cohortId;
    }

    public Cohort getCohort()
    {
        if (_cohort == null)
            _cohort = _cohortId != null ? StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), _cohortId) : null;
        return _cohort;
    }

    public List<String> getAdditionalFormInputHtml()
    {
        return Collections.emptyList();
    }

    public String getStatusFilterName()
    {
        return _statusFilter.name();
    }

    public void setStatusFilterName(String statusFilter)
    {
        _statusFilter = Status.valueOf(statusFilter);
    }

    public Status getStatusFilter()
    {
        return _statusFilter;
    }

    public boolean isViewVialCount()
    {
        return _viewVialCount;
    }

    public void setViewVialCount(boolean viewVialCount)
    {
        _viewVialCount = viewVialCount;
    }

    public boolean isViewParticipantCount()
    {
        return _viewParticipantCount;
    }

    public void setViewParticipantCount(boolean viewParticipantCount)
    {
        _viewParticipantCount = viewParticipantCount;
    }

    public boolean isViewVolume()
    {
        return _viewVolume;
    }

    public void setViewVolume(boolean viewVolume)
    {
        _viewVolume = viewVolume;
    }

    public boolean isViewPtidList()
    {
        return _viewPtidList;
    }

    public void setViewPtidList(boolean viewPtidList)
    {
        _viewPtidList = viewPtidList;
    }

    protected void addBaseFilters(SimpleFilter filter)
    {
        if (allowsAvailabilityFilter() && getStatusFilter() != null)
             addAvailabilityFilter(filter, getStatusFilter());

        if (allowsCohortFilter() && getCohortId() != null)
            addCohortFilter(filter, getCohortId());
    }

    protected void addAvailabilityFilter(SimpleFilter filter, Status status)
    {
        switch (status)
        {
            case ALL:
                break;
            case AVAILABLE:
                filter.addCondition("Available", Boolean.TRUE);
                break;
            case UNAVAILABLE:
                filter.addCondition("Available", Boolean.FALSE);
                break;
        }
    }

    protected void addCohortFilter(SimpleFilter filter, Integer cohortId)
    {
        StudyManager.getInstance().assertCohortsViewable(getContainer(), getUser());
        if (cohortId != null)
        {
            filter.addWhereClause("ptid IN\n" +
                    "(SELECT ParticipantId FROM study.participant WHERE cohortId = ? AND Container = ?)",
                    new Object[] { cohortId, getContainer().getId()});
        }
        else
        {
            filter.addWhereClause("(ptid IN\n" +
                    "(SELECT ParticipantId FROM study.participant WHERE cohortId IS NULL AND Container = ?)" +
                    "OR (ptid NOT IN (SELECT ParticipantId FROM study.participant WHERE Container = ?)))",
                    new Object[] { getContainer().getId(), getContainer().getId()});
        }
    }

    protected String getEnrollmentSitePicker(String inputName, Set<Site> sites, Integer selectedSiteId)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<select name=\"").append(inputName).append("\">");
        builder.append("<option value=\"\">All enrollment sites</option>");
        for (Site site : sites)
        {
            if (site == null)
            {
                builder.append("<option value=\"-1\"");
                if (selectedSiteId != null && selectedSiteId.intValue() == -1)
                    builder.append(" SELECTED");
                builder.append(">Unassigned enrollment site</option>");
            }
            else
            {
                builder.append("<option value=\"").append(site.getRowId()).append("\"");
                if (selectedSiteId != null && selectedSiteId.intValue() == site.getRowId())
                    builder.append(" SELECTED");
                builder.append(">").append(PageFlowUtil.filter(site.getLabel())).append("</option");
            }
        }
        builder.append("</select>");
        return builder.toString();
    }

    protected String getParticipantPicker(String inputName, String selectedParticipantId)
    {
        try
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            StringBuilder builder = new StringBuilder();
            builder.append("<select name=\"").append(inputName).append("\">\n");
            builder.append("<option value=\"\">All Participants (Large Report)</option>\n");
            for (Participant participant : StudyManager.getInstance().getParticipants(study))
            {
                builder.append("<option value=\"").append(PageFlowUtil.filter(participant.getParticipantId())).append("\"");
                if (selectedParticipantId != null && selectedParticipantId.equals(participant.getParticipantId()))
                    builder.append(" SELECTED");
                builder.append(">");
                builder.append(PageFlowUtil.filter(participant.getParticipantId()));
                builder.append("</option>\n");
            }
            builder.append("</select>");
            return builder.toString();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
    
    protected abstract List<? extends SpecimenVisitReport> createReports();

    public abstract String getLabel();

    public boolean allowsCohortFilter()
    {
        return StudyManager.getInstance().showCohorts(getContainer(), getUser());
    }

    public boolean allowsAvailabilityFilter()
    {
        return true;
    }

    public boolean allowsParticipantAggregegates()
    {
        return true;
    }

    public abstract Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction();

    public List<? extends SpecimenVisitReport> getReports()
    {
        if (_reports == null)
            _reports = createReports();
        return _reports;
    }
}
