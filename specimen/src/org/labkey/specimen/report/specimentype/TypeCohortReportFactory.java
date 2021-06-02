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
package org.labkey.specimen.report.specimentype;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenManager;
import org.labkey.specimen.report.SpecimenVisitReport;
import org.labkey.specimen.report.SpecimenVisitReportAction;
import org.labkey.specimen.actions.SpecimenReportActions;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Params;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.CohortService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Select;

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
    public Class<? extends SpecimenVisitReportAction> getAction()
    {
        return SpecimenReportActions.TypeCohortReportAction.class;
    }

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml(User user)
    {
        List<Pair<String, HtmlString>> inputs = super.getAdditionalFormInputHtml(user);
        Study study = StudyService.get().getStudy(getContainer());
        if (study.isAdvancedCohorts())
        {
            CohortFilter.Type currentType = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;
            Input.InputBuilder input = new Input.InputBuilder()
                .type("hidden")
                .value("0")
                .name(Params.cohortId.name());

            Select.SelectBuilder select = new Select.SelectBuilder()
                .name(Params.cohortFilterType.name());

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
        CohortFilter.Type type = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;
        List<CohortFilter> reportCohorts = new ArrayList<>(CohortService.get().getCohortFilters(type, getContainer(), getUser()));
        reportCohorts.add(CohortService.get().getUnassignedCohortFilter());

        List<SpecimenVisitReport> reports = new ArrayList<>();
        for (CohortFilter cohortFilter : reportCohorts)
        {
            Cohort cohort = cohortFilter.getCohort(getContainer(), getUser());
            String title = cohort != null ? cohort.getLabel() : "[No cohort assigned]";
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            addCohortFilter(filter, cohortFilter);
            List<? extends Visit> visits = SpecimenManager.get().getVisitsWithSpecimens(getContainer(), getUser(), cohort);
            reports.add(new TypeCohortReport(title, visits, filter, this, cohortFilter));
        }
        return reports;
    }

    @Override
    public String getLabel()
    {
        CohortFilter filter = getCohortFilter();
        Cohort cohort = filter != null ? filter.getCohort(getContainer(), getUser()) : null;
        return cohort != null ? cohort.getLabel() : "Type by Cohort";
    }

    @Override
    public String getReportType()
    {
        return "TypeByCohort";
    }
}
