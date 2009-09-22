package org.labkey.study.samples.report.specimentype;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.SampleManager;
import org.labkey.study.CohortFilter;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;

import java.util.List;
import java.util.ArrayList;

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
        List<CohortFilter> reportCohorts = new ArrayList<CohortFilter>();
        if (getCohortFilter() != null)
            reportCohorts.add(getCohortFilter());
        else
        {
            for (CohortImpl cohort : StudyManager.getInstance().getCohorts(getContainer(), getUser()))
                reportCohorts.add(new CohortFilter(CohortFilter.Type.DATA_COLLECTION, cohort.getRowId()));
            reportCohorts.add(CohortFilter.UNASSIGNED);
        }

        List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
        for (CohortFilter cohortFilter : reportCohorts)
        {
            CohortImpl cohort = cohortFilter.getCohort(getContainer(), getUser());
            String title = cohort != null ? cohort.getLabel() : "[No cohort assigned]";
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            addCohortFilter(filter, cohortFilter);
            VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), cohort);
            reports.add(new TypeCohortReport(title, visits, filter, this, cohortFilter));
        }
        return reports;
    }

    public String getLabel()
    {
        CohortFilter filter = getCohortFilter();
        CohortImpl cohort = filter != null ? filter.getCohort(getContainer(), getUser()) : null;
        return cohort != null ? cohort.getLabel() : "By Cohort";
    }
}
