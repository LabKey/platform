package org.labkey.study.samples.report.specimentype;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.SampleManager;
import org.labkey.study.CohortFilter;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.util.Pair;
import org.labkey.api.util.PageFlowUtil;

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
    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        List<Pair<String, String>> inputs = super.getAdditionalFormInputHtml();
        StudyImpl study =  StudyManager.getInstance().getStudy(getContainer());
        if (study.isAdvancedCohorts())
        {
            StringBuilder cohortTypeSelect = new StringBuilder();
            CohortFilter.Type currentType = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;

            cohortTypeSelect.append("<input type=\"hidden\" name=\"").append(CohortFilter.Params.cohortId.name()).append("\" value=\"0\">\n");
            cohortTypeSelect.append("<select name=\"").append(CohortFilter.Params.cohortFilterType.name()).append("\">\n");
            for (CohortFilter.Type type : CohortFilter.Type.values())
            {
                cohortTypeSelect.append("\t<option value=\"").append(type.name()).append("\" ");
                cohortTypeSelect.append(type == currentType ? "SELECTED" : "").append(">");
                cohortTypeSelect.append(PageFlowUtil.filter(type.getTitle()));
                cohortTypeSelect.append("</option>\n");
            }
            cohortTypeSelect.append("</select>\n");
            inputs.add(new Pair<String, String>("Cohort Filter Type", cohortTypeSelect.toString()));
        }
        return inputs;
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<CohortFilter> reportCohorts = new ArrayList<CohortFilter>();
        CohortFilter.Type type = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;
        for (CohortImpl cohort : StudyManager.getInstance().getCohorts(getContainer(), getUser()))
            reportCohorts.add(new CohortFilter(type, cohort.getRowId()));
        reportCohorts.add(CohortFilter.UNASSIGNED);

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
