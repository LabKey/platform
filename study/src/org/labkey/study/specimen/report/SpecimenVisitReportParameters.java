/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.study.specimen.report;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.jsp.taglib.AutoCompleteTextTag;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.EnumHasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.element.Option.OptionBuilder;
import org.labkey.api.util.element.Select;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewForm;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.Participant;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.HtmlString.unsafe;

/**
 * User: brittp
 * Created: Jan 14, 2008 12:26:17 PM
 */
public abstract class SpecimenVisitReportParameters extends ViewForm
{
    public static final String DEFAULT_VIEW_ID = "~default~";
    public enum PARAMS
    {
        statusFilterName,
        viewVialCount,
        viewParticipantCount,
        viewVolume,
        baseCustomViewName,
        viewPtidList,
        typeLevel,
        hideEmptyColumns
    }

    public enum Status implements EnumHasHtmlString<Status>
    {
        ALL("All vials"),
        AVAILABLE("Available vials"),
        UNAVAILABLE("Unavailable vials"),
        REQUESTED_INPROCESS("Vials in any request (including in-process)"),
        NOT_REQUESTED_INPROCESS("Vials not in in any request"),
        REQUESTED_COMPLETE("Vials in completed requests"),
        NOT_REQUESTED_COMPLETE("Vials not in completed requests");

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

    private CohortFilter _cohortFilter;
    private Status _statusFilter = Status.ALL;
    private String _baseCustomViewName;
    private boolean _viewVialCount = false;
    private boolean _viewParticipantCount = false;
    private boolean _viewVolume = false;
    private boolean _viewPtidList = false;
    private boolean _hideEmptyColumns;
    private boolean _excelExport;
    private int _participantGroupFilter = -1;
    private List<? extends SpecimenVisitReport> _reports;
    private SpecimenManager.SpecimenTypeLevel _typeLevel = SpecimenManager.SpecimenTypeLevel.Derivative;

    public SpecimenVisitReportParameters()
    {
        _cohortFilter = CohortFilterFactory.getFromURL(getViewContext().getContainer(), getViewContext().getUser(), getViewContext().getActionURL());
    }

    public String getTypeLevel()
    {
        return _typeLevel.name();
    }

    public void setTypeLevel(String typeLevel)
    {
        _typeLevel = SpecimenManager.SpecimenTypeLevel.valueOf(typeLevel);
    }

    public SpecimenManager.SpecimenTypeLevel getTypeLevelEnum()
    {
        return _typeLevel;
    }

    public CohortFilter getCohortFilter()
    {
        return _cohortFilter;
    }

    public CohortImpl getCohort()
    {
        return _cohortFilter != null ? _cohortFilter.getCohort(getContainer(), getUser()) : null;
    }

    public void setCohortFilter(CohortFilter cohortFilter)
    {
        if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
            _cohortFilter = cohortFilter;
    }

    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml()
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

    public boolean isHideEmptyColumns()
    {
        return _hideEmptyColumns;
    }

    public void setHideEmptyColumns(boolean hideEmptyColumns)
    {
        _hideEmptyColumns = hideEmptyColumns;
    }

    public boolean isExcelExport()
    {
        return _excelExport;
    }

    public void setExcelExport(boolean excelExport)
    {
        _excelExport = excelExport;
    }

    public String getBaseCustomViewName()
    {
        return _baseCustomViewName;
    }

    public void setBaseCustomViewName(String baseCustomViewName)
    {
        _baseCustomViewName = baseCustomViewName;
    }

    protected void addBaseFilters(SimpleFilter filter)
    {
        if (allowsAvailabilityFilter() && getStatusFilter() != null)
            addAvailabilityFilter(filter, getStatusFilter());

        if (allowsCohortFilter() && getCohortFilter() != null)
            addCohortFilter(filter, getCohortFilter());

        if (allowsParticipantGroupFilter() && getParticipantGroupFilter() >= 0)
            addParticipantGroupFilter(filter, getParticipantGroupFilter());
    }

    public static final String COMPLETED_REQUESTS_FILTER_SQL =
            "SELECT SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen\n" +
                    "\tJOIN study.SampleRequest ON study.SampleRequestSpecimen.SampleRequestId = study.SampleRequest.RowId\n" +
                    "\tJOIN study.SampleRequestStatus ON study.SampleRequest.StatusId = study.SampleRequestStatus.RowId\n" +
                    "\tWHERE study.SampleRequestStatus.SpecimensLocked = ? AND study.SampleRequestStatus.FinalState = ?\n" +
                    "\tAND study.SampleRequest.Container = ?";

    protected void addAvailabilityFilter(SimpleFilter filter, Status status)
    {
        switch (status)
        {
            case ALL:
                break;
            case AVAILABLE:
                filter.addCondition(FieldKey.fromParts("Available"), Boolean.TRUE);
                break;
            case UNAVAILABLE:
                filter.addCondition(FieldKey.fromParts("Available"), Boolean.FALSE);
                break;
            case REQUESTED_INPROCESS:
                filter.addCondition(FieldKey.fromParts("LockedInRequest"), Boolean.TRUE);
                break;
            case NOT_REQUESTED_INPROCESS:
                filter.addCondition(FieldKey.fromParts("LockedInRequest"), Boolean.FALSE);
                break;
            case REQUESTED_COMPLETE:
                filter.addWhereClause("GlobalUniqueId IN (\n" + COMPLETED_REQUESTS_FILTER_SQL + ")",
                        new Object[] { Boolean.TRUE, Boolean.TRUE, getContainer().getId()});
                break;
            case NOT_REQUESTED_COMPLETE:
                filter.addWhereClause("GlobalUniqueId NOT IN (\n" + COMPLETED_REQUESTS_FILTER_SQL + ")",
                        new Object[] { Boolean.TRUE, Boolean.TRUE, getContainer().getId()});
                break;
        }
    }

    protected void addParticipantGroupFilter(SimpleFilter filter, int ptidListId)
    {
        ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), ptidListId);
        if (group != null)
        {
            StringBuilder sql = new StringBuilder();
            sql.append("(").append(StudyService.get().getSubjectColumnName(getContainer())).append(" IN (SELECT ");
            sql.append("ParticipantId FROM ").append(StudySchema.getInstance().getTableInfoParticipantGroupMap()).append(" WHERE GroupId = ?))");
            filter.addWhereClause(sql.toString(), new Object[] { ptidListId });
        }
    }

    protected void addCohortFilter(SimpleFilter filter, CohortFilter cohortFilter)
    {
        StudyManager.getInstance().assertCohortsViewable(getContainer(), getUser());
        if (cohortFilter == CohortFilterFactory.UNASSIGNED)
        {
            filter.addWhereClause("(" + StudyService.get().getSubjectColumnName(getContainer()) + " IN\n" +
                            "(SELECT ParticipantId FROM study.participant WHERE CurrentCohortId IS NULL AND Container = ?)" +
                            "OR (" + StudyService.get().getSubjectColumnName(getContainer()) +
                            " NOT IN (SELECT ParticipantId FROM study.participant WHERE Container = ?)))",
                    new Object[] { getContainer().getId(), getContainer().getId()});
        }
        else if (cohortFilter != null)
        {
            Cohort cohort = cohortFilter.getCohort(getContainer(),getUser());
            int cohortId = null==cohort ? -1 : cohort.getRowId();

            switch (cohortFilter.getType())
            {
                case DATA_COLLECTION:
                    filter.addWhereClause("CollectionCohort = ? AND Container = ?",
                            new Object[] { cohortId, getContainer().getId()} );
                    break;
                case PTID_CURRENT:
                    filter.addWhereClause(StudyService.get().getSubjectColumnName(getContainer()) + " IN\n" +
                                    "(SELECT ParticipantId FROM study.participant WHERE CurrentCohortId = ? AND Container = ?)",
                            new Object[] { cohortId, getContainer().getId()});
                    break;
                case PTID_INITIAL:
                    filter.addWhereClause(StudyService.get().getSubjectColumnName(getContainer()) + " IN\n" +
                                    "(SELECT ParticipantId FROM study.participant WHERE InitialCohortId = ? AND Container = ?)",
                            new Object[] { cohortId, getContainer().getId()});
                    break;
            }
        }
    }

    protected Pair<String, HtmlString> getEnrollmentSitePicker(String inputName, Set<LocationImpl> locations, Integer selectedSiteId)
    {
        Select.SelectBuilder sb = new Select.SelectBuilder()
            .name(inputName)
            .addOption(new OptionBuilder()
                .value("")
                .label("All enrollment locations")
                .build());

        for (LocationImpl location : locations)
        {
            String label = "Unassigned enrollment location";
            String value = "-1";
            int currentSelectedSite = -1;

            if (location != null)
            {
                label = location.getLabel();
                value = Integer.toString(location.getRowId());
                currentSelectedSite = location.getRowId();
            }

            boolean selected = selectedSiteId != null && selectedSiteId == currentSelectedSite;
            OptionBuilder ob = new OptionBuilder()
                .value(value)
                .label(label)
                .selected(selected);

            sb.addOption(ob.build());
        }

        return new Pair<>("Enrollment site", unsafe(sb.toString()));
    }

    public HtmlString getCustomViewPicker(Map<String, CustomView> specimenDetailViews)
    {
        Select.SelectBuilder sb = new Select.SelectBuilder()
            .name("baseCustomViewName")
            .addOption(new OptionBuilder()
                .value("")
                .label("Base report on all vials")
                .build());

        for (Map.Entry<String, CustomView> viewEntry : specimenDetailViews.entrySet())
        {
            OptionBuilder ob = new OptionBuilder();
            String name = viewEntry.getKey();
            CustomView view = viewEntry.getValue();

            if (view.getFilterAndSort() != null)
            {
                String viewName = DEFAULT_VIEW_ID;
                String label = "Base on default view (filtered)";

                if (name != null)
                {
                    viewName = view.getName();
                    label = "Base on view: " + view.getName();
                }

                boolean selected = _baseCustomViewName != null && _baseCustomViewName.equals(viewName);
                sb.addOption(ob
                    .value(viewName)
                    .label(label)
                    .selected(selected)
                    .build());
            }
        }

        return unsafe(sb.toString());
    }

    private String _allString = null;
    protected String getAllString()
    {
        if (_allString == null)
            _allString = "All " + PageFlowUtil.filter(StudyService.get().getSubjectNounPlural(getContainer())) + " (Large Report)";
        return _allString;
    }

    public boolean isAllSubjectsOption(String subject)
    {
        if (subject == null)
            return false;
        String allString = getAllString().toLowerCase();
        subject = subject.toLowerCase();
        if (subject.length() > allString.length())
            return false;
        return allString.startsWith(subject);
    }

    protected Pair<String, HtmlString> getParticipantPicker(String inputName, String selectedParticipantId)
    {
        Study study = StudyManager.getInstance().getStudy(getContainer());
        Select.SelectBuilder builder = new Select.SelectBuilder();


        String allString = getAllString();
        Collection<Participant> participants = StudyManager.getInstance().getParticipants(study);
        HtmlString particpantPickerValues;
        if (participants.size() <= 200)
        {
            // select the previously selected option or the first non-all option.  We don't want to select 'all participants'
            // by default, since these reports are extremely expensive to generate.
            builder.name(inputName)
                .addOption(new OptionBuilder()
                    .value(allString)
                    .label(allString)
                    .selected(isAllSubjectsOption(selectedParticipantId))
                    .build());

            boolean first = true;
            for (Participant participant : participants)
            {
                boolean isSelected = (selectedParticipantId != null && selectedParticipantId.equals(participant.getParticipantId())) ||
                        (selectedParticipantId == null && first);
                first = false;

                builder.addOption(new OptionBuilder()
                    .value(participant.getParticipantId())
                    .label(DemoMode.id(participant.getParticipantId(), getContainer(), getUser()))
                    .selected(isSelected)
                    .build());
            }
            particpantPickerValues = unsafe(builder.toString());
        }
        else
        {
            String completionUrl = new ActionURL(SpecimenController.CompleteSpecimenAction.class, getContainer()).addParameter("type", "ParticipantId").getLocalURIString();
            String initValue = selectedParticipantId != null ? selectedParticipantId : participants.iterator().next().getParticipantId();

            StringWriter writer = new StringWriter();
            AutoCompleteTextTag tag = new AutoCompleteTextTag()
            {
                @Override
                protected Writer getWriter()
                {
                    return writer;
                }
            };
            tag.setUrl(completionUrl);
            tag.setName(inputName);
            tag.setValue(initValue);

            try
            {
                tag.doTag();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            particpantPickerValues = unsafe(writer.toString());
        }

        return new Pair<>(StudyService.get().getSubjectColumnName(getContainer()), particpantPickerValues);
    }

    protected abstract List<? extends SpecimenVisitReport> createReports();

    public abstract String getLabel();

    /** Internal, stable, unique name used to identify this report type in webpart properties */
    public abstract String getReportType();

    public boolean allowsCohortFilter()
    {
        return StudyManager.getInstance().showCohorts(getContainer(), getUser());
    }

    public boolean allowsAvailabilityFilter()
    {
        return SpecimenManager.getInstance().getRepositorySettings(getContainer()).isEnableRequests();
    }

    public boolean allowsParticipantAggregates()
    {
        return true;
    }

    public boolean allowsCustomViewFilter()
    {
        return true;
    }

    public boolean allowsParticipantGroupFilter()
    {
        return true;
    }

    public int getParticipantGroupFilter()
    {
        return _participantGroupFilter;
    }

    public void setParticipantGroupFilter(int participantGroupFilter)
    {
        _participantGroupFilter = participantGroupFilter;
    }

    public abstract Class<? extends SpecimenController.SpecimenVisitReportAction> getAction();

    public List<? extends SpecimenVisitReport> getReports()
    {
        if (_reports == null)
            _reports = createReports();
        return _reports;
    }
}
