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
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Select;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.SingleCohortFilter;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenVisitReport;

import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.util.HtmlString.unsafe;

/**
 * User: brittp
 * Created: Jan 24, 2008 3:19:29 PM
 */
public class TypeCohortReportFactory extends TypeReportFactory
{
    @Override
    public boolean allowsCohortFilter()
    {
        return false;
    }

    @Override
    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.TypeCohortReportAction.class;
    }

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml()
    {
        List<Pair<String, HtmlString>> inputs = super.getAdditionalFormInputHtml();
        StudyImpl study =  StudyManager.getInstance().getStudy(getContainer());
        if (study.isAdvancedCohorts())
        {
            CohortFilter.Type currentType = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;
            Input.InputBuilder input = new Input.InputBuilder()
                .type("hidden")
                .value("0")
                .name(CohortFilterFactory.Params.cohortId.name());

            Select.SelectBuilder select = new Select.SelectBuilder()
                .name(CohortFilterFactory.Params.cohortFilterType.name());

            for (CohortFilter.Type type : CohortFilter.Type.values())
            {
                select.addOption(new Option.OptionBuilder()
                    .value(type.name())
                    .label(type.getTitle())
                    .selected(type == currentType)
                    .build()
                );
            }

            String combinedElements = input.toString().concat(select.toString());
            inputs.add(new Pair<>("Cohort Filter Type", unsafe(combinedElements)));
        }
        return inputs;
    }

    @Override
    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<CohortFilter> reportCohorts = new ArrayList<>();
        CohortFilter.Type type = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;
        for (CohortImpl cohort : StudyManager.getInstance().getCohorts(getContainer(), getUser()))
            reportCohorts.add(new SingleCohortFilter(type, cohort));
        reportCohorts.add(CohortFilterFactory.UNASSIGNED);

        List<SpecimenVisitReport> reports = new ArrayList<>();
        for (CohortFilter cohortFilter : reportCohorts)
        {
            CohortImpl cohort = cohortFilter.getCohort(getContainer(), getUser());
            String title = cohort != null ? cohort.getLabel() : "[No cohort assigned]";
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            addCohortFilter(filter, cohortFilter);
            List<VisitImpl> visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), cohort);
            reports.add(new TypeCohortReport(title, visits, filter, this, cohortFilter));
        }
        return reports;
    }

    @Override
    public String getLabel()
    {
        CohortFilter filter = getCohortFilter();
        CohortImpl cohort = filter != null ? filter.getCohort(getContainer(), getUser()) : null;
        return cohort != null ? cohort.getLabel() : "Type by Cohort";
    }

    @Override
    public String getReportType()
    {
        return "TypeByCohort";
    }
}
