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
package org.labkey.study.specimen.report.request;

import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DemoMode;
import org.labkey.study.model.Participant;
import org.labkey.study.specimen.report.SpecimenVisitReport;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.SpecimenManager;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;

import java.util.*;

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

    public boolean allowsCohortFilter()
    {
        return false;
    }

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    public boolean allowsParticipantAggregegates()
    {
        return false;
    }

    @Override
    public boolean allowsParticipantGroupFilter()
    {
        return false;
    }

    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        List<Pair<String, String>> inputs = new ArrayList<>();
        inputs.addAll(super.getAdditionalFormInputHtml());
        inputs.add(getParticipantPicker("participantId", _participantId));
        return inputs;
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        String[] participantIds;
        if (!isAllSubjectsOption(_participantId) && _participantId != null && _participantId.trim().length() > 0)
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            Participant participant = StudyManager.getInstance().getParticipant(study, _participantId);
            if (participant == null)
                return Collections.emptyList();
            participantIds = new String[] { _participantId };
        }
        else
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (getParticipantGroupFilter() >= 0)
                participantIds = StudyManager.getInstance().getParticipantIdsForGroup(study, getUser(), getParticipantGroupFilter());
            else
                participantIds = StudyManager.getInstance().getParticipantIds(study,getUser());
            if (participantIds == null)
                return Collections.emptyList();
        }

        List<SpecimenVisitReport> reports = new ArrayList<>();
        Map<Integer, List<VisitImpl>> visitListCache = new HashMap<>(); // cohort rowId -> visit
        boolean showCohorts = StudyManager.getInstance().showCohorts(getContainer(), getUser());
        List<VisitImpl> allVisits = null;
        Study study = StudyManager.getInstance().getStudy(getContainer());
        for (String participantId : participantIds)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())), participantId);
            addBaseFilters(filter);
            List<VisitImpl> visits = null;
            if (showCohorts)
            {
                CohortImpl cohort = StudyManager.getInstance().getCurrentCohortForParticipant(getContainer(), getUser(), participantId);
                if (cohort != null)
                {
                    visits = visitListCache.get(cohort.getRowId());
                    if (visits == null)
                    {
                        visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), cohort);
                        visitListCache.put(cohort.getRowId(), visits);
                    }
                }
            }

            if (visits == null)
            {
                if (allVisits == null)
                    allVisits = StudyManager.getInstance().getVisits(study, Visit.Order.DISPLAY);
                visits = allVisits;
            }
            reports.add(new RequestParticipantReport(DemoMode.id(participantId, getContainer(), getUser()), visits, filter, this));
        }
        return reports;
    }

    public String getLabel()
    {
        return "Requested by " + StudyService.get().getSubjectNounSingular(getContainer());
    }

    @Override
    public String getReportType()
    {
        return "RequestedByParticipant";
    }

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.RequestParticipantReportAction.class;
    }
}
