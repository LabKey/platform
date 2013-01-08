/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.study.samples.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.study.Location;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.samples.report.SpecimenVisitReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:40 PM
 */
public class RequestSiteReportFactory extends BaseRequestReportFactory
{
    private Integer _siteId;

    public Integer getSiteId()
    {
        return _siteId;
    }

    public void setSiteId(Integer siteId)
    {
        _siteId = siteId;
    }

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    public String getLabel()
    {
        Location location = _siteId != null ? StudyManager.getInstance().getLocation(getContainer(), _siteId) : null;
        return "By Requesting Location" + (location != null ? ": " + location.getLabel() : "");
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        LocationImpl[] locations;
        if (getSiteId() != null)
            locations = new LocationImpl[] { StudyManager.getInstance().getLocation(getContainer(), getSiteId()) };
        else
            locations = SampleManager.getInstance().getSitesWithRequests(getContainer());
        if (locations == null)
            return Collections.emptyList();
        List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
        VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
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
            reports.add(new RequestSiteReport(location.getLabel(), filter, this, visits, location.getRowId(), isCompletedRequestsOnly()));
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
        builder.append("<select name=\"siteId\">\n" +
                "<option value=\"\">All Requesting Locations</option>\n");
        for (LocationImpl location : SampleManager.getInstance().getSitesWithRequests(getContainer()))
        {
            builder.append("<option value=\"").append(location.getRowId()).append("\"");
            if (_siteId != null && location.getRowId() == _siteId)
                builder.append(" SELECTED");
            builder.append(">");
            builder.append(PageFlowUtil.filter(location.getLabel()));
            builder.append("</option>\n");
        }
        builder.append("</select>");
        List<Pair<String, String>> inputs = new ArrayList<Pair<String, String>>(super.getAdditionalFormInputHtml());
        inputs.add(new Pair<String, String>("Requesting location(s)", builder.toString()));
        return inputs;
    }
}