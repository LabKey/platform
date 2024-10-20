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
import org.labkey.api.study.Location;
import org.labkey.api.study.Visit;
import org.labkey.specimen.SpecimenManager;
import org.labkey.specimen.actions.SpecimenReportActions;
import org.labkey.specimen.report.SpecimenVisitReport;
import org.labkey.specimen.report.SpecimenVisitReportAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:40 PM
 */
public class RequestReportFactory extends BaseRequestReportFactory
{
    @Override
    public String getLabel()
    {
        return "Requested Summary";
    }

    @Override
    public String getReportType()
    {
        return "RequestedSummary";
    }

    @Override
    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    @Override
    protected List<? extends SpecimenVisitReport> createReports()
    {
        Location[] locations = SpecimenManager.get().getSitesWithRequests(getContainer());
        if (locations == null)
            return Collections.emptyList();
        List<SpecimenVisitReport> reports = new ArrayList<>();
        List<? extends Visit> visits = SpecimenManager.get().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        SimpleFilter filter = new SimpleFilter();
        if (isCompletedRequestsOnly())
        {
            filter.addWhereClause("globaluniqueid IN\n" +
                    "(" + COMPLETED_REQUESTS_FILTER_SQL + ")",
                    new Object[] { Boolean.TRUE, Boolean.TRUE, getContainer().getId()});
        }
        else
        {
            filter.addWhereClause("globaluniqueid IN\n" +
                    "(SELECT specimenglobaluniqueid FROM study.samplerequestspecimen WHERE container = ?) and LockedInRequest = ?",
                    new Object[] { getContainer().getId(), Boolean.TRUE });
        }
        addBaseFilters(filter);
        reports.add(new RequestReport("All Requested Specimens", filter, this, visits, isCompletedRequestsOnly()));
        return reports;
    }

    @Override
    public Class<? extends SpecimenVisitReportAction> getAction()
    {
        return SpecimenReportActions.RequestReportAction.class;
    }
}
