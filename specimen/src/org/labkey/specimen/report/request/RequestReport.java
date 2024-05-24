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
package org.labkey.specimen.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.study.Visit;
import org.labkey.specimen.report.SpecimenTypeVisitReport;
import org.labkey.specimen.report.SpecimenVisitReportParameters;
import org.labkey.specimen.report.SummaryByVisitType;

import java.util.List;

/**
 * User: brittp
 * Created: Jan 14, 2008 1:37:24 PM
 */
public class RequestReport extends SpecimenTypeVisitReport
{
    private final boolean _completedRequestsOnly;

    public RequestReport(String titlePrefix, SimpleFilter filter, SpecimenVisitReportParameters parameters, List<? extends Visit> visits, boolean completedRequestsOnly)
    {
        super(titlePrefix, visits, filter, parameters);
        _completedRequestsOnly = completedRequestsOnly;
    }

    @Override
    protected String getFilterQueryString(Visit visit, SummaryByVisitType summary)
    {
        return super.getFilterQueryString(visit, summary)  + "&" +
                (_completedRequestsOnly ? SpecimenQueryView.PARAMS.showCompleteRequestedOnly  : SpecimenQueryView.PARAMS.showRequestedOnly) + "=true";
    }
}