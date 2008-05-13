package org.labkey.study.samples.report.request;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.samples.report.request.RequestReport;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.model.Site;
import org.labkey.study.model.Visit;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.sql.SQLException;

/**
 * Copyright (c) 2008 LabKey Corporation
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
* Created: Jan 24, 2008 1:38:40 PM
*/
public class RequestReportFactory extends SpecimenVisitReportParameters
{
    public String getLabel()
    {
        return "Request Summary";
    }

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        try
        {
            Site[] sites = SampleManager.getInstance().getSitesWithRequests(getContainer());
            if (sites == null)
                return Collections.emptyList();
            List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
            Visit[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getCohort());
                SimpleFilter filter = new SimpleFilter();
                filter.addWhereClause("globaluniqueid IN\n" +
                        "(SELECT specimenglobaluniqueid FROM study.samplerequestspecimen WHERE container = ?)",
                        new Object[] { getContainer().getId()});
                addBaseFilters(filter);
                reports.add(new RequestReport("All Requested Specimens", filter, this, visits));
            return reports;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.RequestReportAction.class;
    }
}
