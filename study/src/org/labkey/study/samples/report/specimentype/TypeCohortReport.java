package org.labkey.study.samples.report.specimentype;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.CompareType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenTypeVisitReport;
import org.labkey.study.CohortFilter;

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
 * Created: Jan 14, 2008 1:37:24 PM
 */
public class TypeCohortReport extends SpecimenTypeVisitReport
{
    private CohortFilter _cohortFilter;

    public TypeCohortReport(String titlePrefix, VisitImpl[] visits, SimpleFilter filter, SpecimenVisitReportParameters parameters, CohortFilter cohortFilter)
    {
        super(titlePrefix, visits, filter, parameters);
        _cohortFilter = cohortFilter;
    }

    protected SimpleFilter getViewFilter()
    {
        SimpleFilter filter = super.getViewFilter();
        if (_cohortFilter != null)
        {
            if (_cohortFilter == CohortFilter.UNASSIGNED)
                filter.addCondition(_cohortFilter.getType().getFilterColumn().toString(), null, CompareType.ISBLANK);
            else
                filter.addCondition(_cohortFilter.getType().getFilterColumn().toString(), _cohortFilter.getCohortId());
        }
        return filter;
    }
}
