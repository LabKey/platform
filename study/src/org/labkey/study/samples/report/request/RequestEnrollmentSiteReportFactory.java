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
import org.labkey.api.study.Site;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Set;
import java.sql.SQLException;

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
* Created: Jan 24, 2008 1:38:40 PM
*/
public class RequestEnrollmentSiteReportFactory extends TypeReportFactory
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

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    public String getLabel()
    {
        try
        {
            Site site = _enrollmentSiteId != null ? StudyManager.getInstance().getSite(getContainer(), _enrollmentSiteId) : null;
            return "Requests by Enrollment Site" + (site != null ? ": " + site.getLabel() : "");
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
            Set<SiteImpl> sites;
            if (getEnrollmentSiteId() != null)
                sites = Collections.singleton(StudyManager.getInstance().getSite(getContainer(), getEnrollmentSiteId()));
            else
                sites = SampleManager.getInstance().getEnrollmentSitesWithRequests(getContainer());
            if (sites == null)
                return Collections.emptyList();
            List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
            VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
            for (SiteImpl site : sites)
            {
                SimpleFilter filter = new SimpleFilter();
                String sql = "GlobalUniqueId IN (SELECT Specimen.GlobalUniqueId FROM study.Specimen AS Specimen,\n" +
                        "study.SampleRequestSpecimen AS RequestSpecimen,\n" +
                        "study.SampleRequest AS Request, study.SampleRequestStatus AS Status,\n" +
                        "study.Participant AS Participant\n" +
                        "WHERE Request.Container = Status.Container AND\n" +
                        "     Request.StatusId = Status.RowId AND\n" +
                        "     RequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                        "     RequestSpecimen.Container = Request.Container AND\n" +
                        "     Specimen.Container = RequestSpecimen.Container AND\n" +
                        "     Specimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                        "     Participant.Container = Specimen.Container AND\n" +
                        "     Participant.ParticipantId = Specimen.Ptid AND\n" +
                        "     Status.SpecimensLocked = ? AND\n" +
                        "     Specimen.Container = ? AND\n" +
                        "     Participant.EnrollmentSiteId ";
                Object[] params;
                if (site == null)
                {
                    sql += "IS NULL)";
                    params = new Object[] { Boolean.TRUE, getContainer().getId()};
                }
                else
                {
                    sql += "= ?)";
                    params = new Object[] { Boolean.TRUE, getContainer().getId(), site.getRowId() };
                }

                filter.addWhereClause(sql, params, "GlobalUniqueId");
                addBaseFilters(filter);
                reports.add(new RequestEnrollmentSiteReport(site == null ? "[Unassigned enrollment site]" : site.getLabel(),
                        filter, this, visits, site != null ? site.getRowId() : -1));
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
        return SpringSpecimenController.RequestEnrollmentSiteReportAction.class;
    }

    public List<String> getAdditionalFormInputHtml()
    {
        List<String> inputs = new ArrayList<String>(super.getAdditionalFormInputHtml());
        Set<SiteImpl> sites = SampleManager.getInstance().getEnrollmentSitesWithRequests(getContainer());
        inputs.add(getEnrollmentSitePicker("enrollmentSiteId", sites, _enrollmentSiteId));
        return inputs;
    }
}