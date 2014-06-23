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
package org.labkey.study.specimen.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.study.Location;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenVisitReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:40 PM
 */
public class RequestReportFactory extends BaseRequestReportFactory
{
    public String getLabel()
    {
        return "Requested Summary";
    }

    @Override
    public String getReportType()
    {
        return "RequestedSummary";
    }

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        Location[] locations = SpecimenManager.getInstance().getSitesWithRequests(getContainer());
        if (locations == null)
            return Collections.emptyList();
        List<SpecimenVisitReport> reports = new ArrayList<>();
        List<VisitImpl> visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
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

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.RequestReportAction.class;
    }
}
