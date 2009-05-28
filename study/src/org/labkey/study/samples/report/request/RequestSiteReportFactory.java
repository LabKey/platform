package org.labkey.study.samples.report.request;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.samples.report.specimentype.TypeReportFactory;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.Site;

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
public class RequestSiteReportFactory extends TypeReportFactory
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
        try
        {
            Site site = _siteId != null ? StudyManager.getInstance().getSite(getContainer(), _siteId) : null;
            return "By Requesting Location" + (site != null ? ": " + site.getLabel() : "");
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        try
        {
            SiteImpl[] sites;
            if (getSiteId() != null)
                sites = new SiteImpl[] { StudyManager.getInstance().getSite(getContainer(), getSiteId()) };
            else
                sites = SampleManager.getInstance().getSitesWithRequests(getContainer());
            if (sites == null)
                return Collections.emptyList();
            List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
            VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getCohort());
            for (SiteImpl site : sites)
            {
                SimpleFilter filter = new SimpleFilter();
                filter.addWhereClause("globaluniqueid IN\n" +
                        "(\n" +
                        "     SELECT specimenglobaluniqueid FROM study.samplerequestspecimen WHERE samplerequestid IN\n" +
                        "     (\n" +
                        "          SELECT r.rowid FROM study.SampleRequest r JOIN study.SampleRequestStatus rs ON\n" +
                        "           r.StatusId = rs.RowId AND rs.SpecimensLocked = ? WHERE destinationsiteid = ? AND r.container = ?\n" +
                        "     )\n" +
                        ")", new Object[] { Boolean.TRUE, site.getRowId(), getContainer().getId()});
                addBaseFilters(filter);
                reports.add(new RequestSiteReport(site.getLabel(), filter, this, visits, site.getRowId()));
            }
            return reports;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.RequestSiteReportAction.class;
    }

    public List<String> getAdditionalFormInputHtml()
    {
        try
        {
            StringBuilder builder = new StringBuilder();
            builder.append("<select name=\"siteId\">\n" +
                    "<option value=\"\">All Requesting Locations</option>\n");
            for (SiteImpl site : SampleManager.getInstance().getSitesWithRequests(getContainer()))
            {
                builder.append("<option value=\"").append(site.getRowId()).append("\"");
                if (_siteId != null && site.getRowId() == _siteId)
                    builder.append(" SELECTED");
                builder.append(">");
                builder.append(PageFlowUtil.filter(site.getLabel()));
                builder.append("</option>\n");
            }
            builder.append("</select>");
            List<String> inputs = new ArrayList<String>(super.getAdditionalFormInputHtml());
            inputs.add(builder.toString());
            return inputs;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }
}