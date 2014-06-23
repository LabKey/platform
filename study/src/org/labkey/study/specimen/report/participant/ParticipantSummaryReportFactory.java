/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.study.specimen.report.participant;

import org.labkey.study.specimen.report.SpecimenVisitReportParameters;
import org.labkey.study.specimen.report.SpecimenVisitReport;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.SpecimenManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.study.StudyService;

import java.util.List;
import java.util.Collections;

/**
 * User: brittp
 * Created: Jan 29, 2008 5:56:06 PM
 */
public class ParticipantSummaryReportFactory extends SpecimenVisitReportParameters
{
    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<VisitImpl> visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        SimpleFilter filter = new SimpleFilter();
        addBaseFilters(filter);
        ParticipantVisitReport report = new ParticipantVisitReport("Summary", visits, filter, this);
        return Collections.singletonList(report);
    }

    public String getLabel()
    {
        return StudyService.get().getSubjectNounSingular(getContainer()) + " Summary";
    }

    @Override
    public String getReportType()
    {
        return "ParticipantSummary";
    }

    public boolean allowsParticipantAggregegates()
    {
        return false;
    }
    
    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.ParticipantSummaryReportAction.class;
    }
}
