package org.labkey.study.samples.report.request;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.samples.report.specimentype.TypeReportFactory;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.SampleManager;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.Visit;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.RuntimeSQLException;

import java.util.*;
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
 * Created: Feb 5, 2008 2:03:52 PM
 */
public class RequestParticipantReportFactory extends TypeReportFactory
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

    public List<String> getAdditionalFormInputHtml()
    {
        List<String> inputs = new ArrayList<String>();
        inputs.addAll(super.getAdditionalFormInputHtml());
        inputs.add(getParticipantPicker("participantId", _participantId));
        return inputs;
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        String[] participantIds;
        if (_participantId != null && _participantId.trim().length() > 0)
            participantIds = new String[] { _participantId };
        else
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            participantIds = StudyManager.getInstance().getParticipantIds(study);
            if (participantIds == null)
                return Collections.<SpecimenVisitReport>emptyList();
        }

        List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
        Map<Cohort, Visit[]> visitListCache = new HashMap<Cohort, Visit[]>();
        boolean showCohorts = StudyManager.getInstance().showCohorts(getContainer(), getUser());
        Visit[] allVisits = null;
        Study study = StudyManager.getInstance().getStudy(getContainer());
        for (String participantId : participantIds)
        {
            SimpleFilter filter = new SimpleFilter("ptid", participantId);
            addBaseFilters(filter);
            try
            {
                Visit[] visits;
                if (showCohorts)
                {
                    Cohort cohort = StudyManager.getInstance().getCohortForParticipant(getContainer(), getUser(), participantId);
                    visits = visitListCache.get(cohort);
                    if (visits == null)
                    {
                        visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), cohort);
                        visitListCache.put(cohort, visits);
                    }
                }
                else
                {
                    if (allVisits == null)
                        allVisits = StudyManager.getInstance().getVisits(study);
                    visits = allVisits;
                }
                reports.add(new RequestParticipantReport(participantId, visits, filter, this));
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return reports;
    }

    public String getLabel()
    {
        return "Requests by Participant";
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.RequestParticipantReportAction.class;
    }
}
