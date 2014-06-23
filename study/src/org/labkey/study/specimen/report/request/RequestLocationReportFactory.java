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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenVisitReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:40 PM
 */
public class RequestLocationReportFactory extends BaseRequestReportFactory
{
    private Integer _locationId;

    public Integer getLocationId()
    {
        return _locationId;
    }

    public void setLocationId(Integer locationId)
    {
        _locationId = locationId;
    }

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    public String getLabel()
    {
        Location location = _locationId != null ? StudyManager.getInstance().getLocation(getContainer(), _locationId) : null;
        return "Requested by Requesting Location" + (location != null ? ": " + location.getLabel() : "");
    }

    @Override
    public String getReportType()
    {
        return "RequestedByRequestingLocation";
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        LocationImpl[] locations;
        if (getLocationId() != null)
            locations = new LocationImpl[] { StudyManager.getInstance().getLocation(getContainer(), getLocationId()) };
        else
            locations = SpecimenManager.getInstance().getSitesWithRequests(getContainer());
        if (locations == null)
            return Collections.emptyList();
        List<SpecimenVisitReport> reports = new ArrayList<>();
        List<VisitImpl> visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        for (LocationImpl location : locations)
        {
            SimpleFilter filter = new SimpleFilter();
            Object[] params;
            if (isCompletedRequestsOnly())
                params = new Object[] { Boolean.TRUE, Boolean.TRUE, location.getRowId(), getContainer().getId()};
            else
                params = new Object[] { Boolean.TRUE, location.getRowId(), getContainer().getId()};
            filter.addWhereClause("globaluniqueid IN\n" +
                    "(\n" +
                    "     SELECT specimenglobaluniqueid FROM study.samplerequestspecimen WHERE samplerequestid IN\n" +
                    "     (\n" +
                    "          SELECT r.rowid FROM study.SampleRequest r JOIN study.SampleRequestStatus rs ON\n" +
                    "           r.StatusId = rs.RowId\n" +
                    "           AND rs.SpecimensLocked = ?\n" +
                    (isCompletedRequestsOnly() ? "           AND rs.FinalState = ?\n" : "") +
                    "           WHERE destinationsiteid = ? AND r.container = ?\n" +
                    "     )\n" +
                    ")", params);
            addBaseFilters(filter);
            reports.add(new RequestLocationReport(location.getLabel(), filter, this, visits, location.getRowId(), isCompletedRequestsOnly()));
        }
        return reports;
    }

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.RequestSiteReportAction.class;
    }

    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<select name=\"locationId\">\n" +
                "<option value=\"\">All Requesting Locations</option>\n");
        for (LocationImpl location : SpecimenManager.getInstance().getSitesWithRequests(getContainer()))
        {
            builder.append("<option value=\"").append(location.getRowId()).append("\"");
            if (_locationId != null && location.getRowId() == _locationId)
                builder.append(" SELECTED");
            builder.append(">");
            builder.append(PageFlowUtil.filter(location.getLabel()));
            builder.append("</option>\n");
        }
        builder.append("</select>");
        List<Pair<String, String>> inputs = new ArrayList<>(super.getAdditionalFormInputHtml());
        inputs.add(new Pair<>("Requesting location(s)", builder.toString()));
        return inputs;
    }
}