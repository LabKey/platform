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
package org.labkey.api.specimen.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenManager;
import org.labkey.api.specimen.report.SpecimenVisitReport;
import org.labkey.api.specimen.report.SpecimenVisitReportAction;
import org.labkey.api.specimen.actions.SpecimenReportActions;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.CohortService;
import org.labkey.api.study.model.ParticipantGroupService;
import org.labkey.api.study.model.VisitService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Created: Feb 5, 2008 2:03:52 PM
 */
public class RequestParticipantReportFactory extends BaseRequestReportFactory
{
    private String _participantId;

    public String getParticipantId()
    {
        return _participantId;
    }

    public void setParticipantId(String participantId)
    {
        _participantId = participantId;
    }

    @Override
    public boolean allowsCohortFilter()
    {
        return false;
    }

    @Override
    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    @Override
    public boolean allowsParticipantAggregates()
    {
        return false;
    }

    @Override
    public boolean allowsParticipantGroupFilter()
    {
        return false;
    }

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml(User user)
    {
        List<Pair<String, HtmlString>> inputs = new ArrayList<>(super.getAdditionalFormInputHtml(user));
        inputs.add(getParticipantPicker("participantId", _participantId));
        return inputs;
    }

    @Override
    protected List<? extends SpecimenVisitReport> createReports()
    {
        Collection<String> participantIds;
        if (!isAllSubjectsOption(_participantId) && _participantId != null && _participantId.trim().length() > 0)
        {
            Study study = StudyService.get().getStudy(getContainer());
            if (!StudyService.get().participantExists(study, _participantId))
                return Collections.emptyList();
            participantIds = List.of(_participantId);
        }
        else
        {
            Study study = StudyService.get().getStudy(getContainer());
            if (getParticipantGroupFilter() >= 0)
                participantIds = ParticipantGroupService.get().getParticipantIdsForGroup(study, getUser(), getParticipantGroupFilter());
            else
                participantIds = StudyService.get().getParticipantIds(study, getUser());
            if (participantIds == null)
                return Collections.emptyList();
        }

        List<SpecimenVisitReport> reports = new ArrayList<>();
        Map<Integer, List<? extends Visit>> visitListCache = new HashMap<>(); // cohort rowId -> visit
        boolean showCohorts = StudyService.get().showCohorts(getContainer(), getUser());
        List<? extends Visit> allVisits = null;
        Study study = StudyService.get().getStudy(getContainer());
        for (String participantId : participantIds)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())), participantId);
            addBaseFilters(filter);
            List<? extends Visit> visits = null;
            if (showCohorts)
            {
                Cohort cohort = CohortService.get().getCurrentCohortForParticipant(getContainer(), getUser(), participantId);
                if (cohort != null)
                {
                    visits = visitListCache.get(cohort.getRowId());
                    if (visits == null)
                    {
                        visits = SpecimenManager.get().getVisitsWithSpecimens(getContainer(), getUser(), cohort);
                        visitListCache.put(cohort.getRowId(), visits);
                    }
                }
            }

            if (visits == null)
            {
                if (allVisits == null)
                    allVisits = VisitService.get().getVisits(study, Visit.Order.DISPLAY);
                visits = allVisits;
            }
            reports.add(new RequestParticipantReport(DemoMode.id(participantId, getContainer(), getUser()), visits, filter, this));
        }
        return reports;
    }

    @Override
    public String getLabel()
    {
        return "Requested by " + StudyService.get().getSubjectNounSingular(getContainer());
    }

    @Override
    public String getReportType()
    {
        return "RequestedByParticipant";
    }

    @Override
    public Class<? extends SpecimenVisitReportAction> getAction()
    {
        return SpecimenReportActions.RequestParticipantReportAction.class;
    }
}
