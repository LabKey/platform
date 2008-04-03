package org.labkey.study.samples.report.specimentype;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Copyright (c) 2007 LabKey Software Foundation
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
 * Created: Jan 24, 2008 3:19:29 PM
 */
public class TypeCohortReportFactory extends TypeReportFactory
{
    public boolean allowsCohortFilter()
    {
        return false;
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.TypeCohortReportAction.class;
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<Cohort> reportCohorts = new ArrayList<Cohort>();
        if (getCohort() != null)
            reportCohorts.add(getCohort());
        else
            reportCohorts.addAll(Arrays.asList(StudyManager.getInstance().getCohorts(getContainer(), getUser())));
        // add null cohort so we can view unassigned participants
        reportCohorts.add(null);

        List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
        for (Cohort cohort : reportCohorts)
        {
            String title = cohort != null ? cohort.getLabel() : "[No cohort assigned]";
            Integer cohortId = cohort != null ? cohort.getRowId() : null;
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            addCohortFilter(filter, cohortId);
            Visit[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), cohort);
            reports.add(new TypeCohortReport(title, visits, filter, this, cohortId));
        }
        return reports;
    }

    public String getLabel()
    {
        return getCohort() != null ? getCohort().getLabel() : "By Cohort";
    }
}
