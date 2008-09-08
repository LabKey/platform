package org.labkey.study.samples.report.specimentype;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.samples.report.SpecimenTypeVisitReport;
import org.labkey.study.model.Visit;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;

import java.util.List;
import java.util.Collections;

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
* Created: Jan 24, 2008 1:38:06 PM
*/
public class TypeSummaryReportFactory extends TypeReportFactory
{
    public String getLabel()
    {
        return "Summary Report";
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        Visit[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getCohort());
        SimpleFilter filter = new SimpleFilter();
        addBaseFilters(filter);
        SpecimenTypeVisitReport report = new SpecimenTypeVisitReport("Summary", visits, filter, this);
        return Collections.singletonList(report);
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.TypeSummaryReportAction.class;
    }
}
