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
package org.labkey.study.specimen.report.specimentype;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.study.CohortFilter;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenTypeVisitReport;
import org.labkey.study.specimen.report.SpecimenVisitReportParameters;

import java.util.List;

/**
 * User: brittp
 * Created: Jan 14, 2008 1:37:24 PM
 */
public class TypeCohortReport extends SpecimenTypeVisitReport
{
    private CohortFilter _cohortFilter;

    public TypeCohortReport(String titlePrefix, List<VisitImpl> visits, SimpleFilter filter, SpecimenVisitReportParameters parameters, CohortFilter cohortFilter)
    {
        super(titlePrefix, visits, filter, parameters);
        _cohortFilter = cohortFilter;
    }

    // override addCohortURLFilter to use the filter we're passed, rather than the cohort filter on the base
    // parameters object (the base parameter filter just contains the type, since this report can render multiple
    // reports, one for each cohort):
    @Override
    protected void addCohortURLFilter(Study study, ActionURL url)
    {
        if (_cohortFilter != null)
            _cohortFilter.addURLParameters(study, url, null);
    }
}
