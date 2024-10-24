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

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.study.Location;
import org.labkey.api.study.Visit;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.specimen.SpecimenManager;
import org.labkey.specimen.actions.SpecimenReportActions;
import org.labkey.specimen.report.SpecimenVisitReport;
import org.labkey.specimen.report.SpecimenVisitReportAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:40 PM
 */
public class RequestEnrollmentSiteReportFactory extends BaseRequestReportFactory
{
    private Integer _enrollmentSiteId;

    public Integer getEnrollmentSiteId()
    {
        return _enrollmentSiteId;
    }

    public void setEnrollmentSiteId(Integer enrollmentSiteId)
    {
        _enrollmentSiteId = enrollmentSiteId;
    }

    @Override
    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    @Override
    public String getLabel()
    {
        Location location = _enrollmentSiteId != null ? LocationManager.get().getLocation(getContainer(), _enrollmentSiteId) : null;
        return "Requested by Enrollment Location" + (location != null ? ": " + location.getLabel() : "");
    }

    @Override
    public String getReportType()
    {
        return "RequestedByEnrollmentLocation";
    }

    @Override
    protected List<? extends SpecimenVisitReport> createReports()
    {
        final Set<LocationImpl> locations;

        if (getEnrollmentSiteId() != null)
        {
            locations = Collections.singleton(LocationManager.get().getLocation(getContainer(), getEnrollmentSiteId()));
        }
        else
        {
            locations = SpecimenManager.get().getEnrollmentSitesWithRequests(getContainer(), getUser());
            // add null to the set so we can search for ptid without an enrollment site:
            locations.add(null);
        }

        List<SpecimenVisitReport> reports = new ArrayList<>();
        List<? extends Visit> visits = SpecimenManager.get().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());

        SQLFragment baseSql = SpecimenQueryView.getBaseRequestedEnrollmentSql(getContainer(), getUser(), isCompletedRequestsOnly());

        for (LocationImpl location : locations)
        {
            SimpleFilter filter = new SimpleFilter();
            SQLFragment sql = new SQLFragment(baseSql);
            if (location == null)
                sql.append("IS NULL)");
            else
            {
                sql.append("= " + location.getRowId() + ")");
            }

            filter.addWhereClause(sql, FieldKey.fromParts("GlobalUniqueId"));
            addBaseFilters(filter);
            reports.add(new RequestEnrollmentLocationReport(location == null ? "[Unassigned enrollment location]" : location.getLabel(),
                    filter, this, visits, location != null ? location.getRowId() : -1, isCompletedRequestsOnly()));
        }
        return reports;
    }

    @Override
    public Class<? extends SpecimenVisitReportAction> getAction()
    {
        return SpecimenReportActions.RequestEnrollmentSiteReportAction.class;
    }

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml(User user)
    {
        List<Pair<String, HtmlString>> inputs = new ArrayList<>(super.getAdditionalFormInputHtml(user));
        Set<LocationImpl> locations = SpecimenManager.get().getEnrollmentSitesWithRequests(getContainer(), getUser());
        // add null to the set so we can search for ptid without an enrollment site:
        locations.add(null);
        inputs.add(getEnrollmentSitePicker("enrollmentSiteId", locations, _enrollmentSiteId));
        return inputs;
    }
}
