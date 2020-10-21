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
package org.labkey.study.specimen.report.specimentype;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.Participant;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenTypeVisitReport;
import org.labkey.study.specimen.report.SpecimenVisitReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:15 PM
 */
public class TypeParticipantReportFactory extends TypeReportFactory
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
    public String getLabel()
    {
        String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
        return _participantId == null || isAllSubjectsOption(_participantId) ? "Type by " + subjectNoun : subjectNoun + " " + DemoMode.id(_participantId, getContainer(), getUser());
    }

    @Override
    public String getReportType()
    {
        return "TypeByParticipant";
    }

    @Override
    public boolean allowsCohortFilter()
    {
        return false;
    }

    @Override
    public boolean allowsParticipantAggregates()
    {
        return false;
    }

    @Override
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
                participantIds = StudyManager.getInstance().getParticipantIds(study, getUser());
            if (participantIds == null)
                return Collections.emptyList();
        }
        List<SpecimenVisitReport> reports = new ArrayList<>();
        Map<Integer, List<VisitImpl>> visitListCache = new HashMap<>(); // cohort rowId -> visits
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
            reports.add(new SpecimenTypeVisitReport(DemoMode.id(participantId, getContainer(), getUser()), visits, filter, this));
        }
        return reports;
    }

    @Override
    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.TypeParticipantReportAction.class;
    }

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml()
    {
        List<Pair<String, HtmlString>> inputs = new ArrayList<>(super.getAdditionalFormInputHtml());
        inputs.add(getParticipantPicker("participantId", _participantId));
        return inputs;
    }
}
