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
import org.labkey.api.study.Visit;
import org.labkey.specimen.SpecimenManager;
import org.labkey.specimen.actions.SpecimenReportActions;
import org.labkey.specimen.report.SpecimenTypeVisitReport;
import org.labkey.specimen.report.SpecimenVisitReport;
import org.labkey.specimen.report.SpecimenVisitReportAction;

import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:06 PM
 */
public class TypeSummaryReportFactory extends TypeReportFactory
{
    @Override
    public String getLabel()
    {
        return "Type Summary Report";
    }

    @Override
    public String getReportType()
    {
        return "TypeSummary";
    }

    @Override
    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<? extends Visit> visits = SpecimenManager.get().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        SimpleFilter filter = new SimpleFilter();
        addBaseFilters(filter);
        SpecimenTypeVisitReport report = new SpecimenTypeVisitReport("Summary", visits, filter, this);
        return Collections.singletonList(report);
    }

    @Override
    public Class<? extends SpecimenVisitReportAction> getAction()
    {
        return SpecimenReportActions.TypeSummaryReportAction.class;
    }
}
