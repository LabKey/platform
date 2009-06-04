package org.labkey.study.samples.report.participant;

import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.SampleManager;
import org.labkey.api.data.SimpleFilter;

import java.util.List;
import java.util.Collections;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
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
 * Created: Jan 29, 2008 5:56:06 PM
 */
public class ParticipantSummaryReportFactory extends SpecimenVisitReportParameters
{
    protected List<? extends SpecimenVisitReport> createReports()
    {
        VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        SimpleFilter filter = new SimpleFilter();
        addBaseFilters(filter);
        ParticipantVisitReport report = new ParticipantVisitReport("Summary", visits, filter, this);
        return Collections.singletonList(report);
    }

    public String getLabel()
    {
        return "Participant Summary";
    }

    public boolean allowsParticipantAggregegates()
    {
        return false;
    }
    
    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.ParticipantSummaryReportAction.class;
    }
}
