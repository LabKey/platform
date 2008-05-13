package org.labkey.study.samples.report.participant;

import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.model.Visit;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Site;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.RuntimeSQLException;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
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
 * Created: Jan 30, 2008 3:40:07 PM
 */
public class ParticipantSiteReportFactory extends SpecimenVisitReportParameters
{
    private Integer _enrollmentSiteId;

    protected List<? extends SpecimenVisitReport> createReports()
    {
        Visit[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getCohort());
        List<ParticipantVisitReport> reports = new ArrayList<ParticipantVisitReport>();
        Set<Site> enrollmentSites;
        if (_enrollmentSiteId == null)
            enrollmentSites = SampleManager.getInstance().getEnrollmentSitesWithSpecimens(getContainer());
        else if (_enrollmentSiteId == -1)
        {
            enrollmentSites = Collections.singleton(null);
        }
        else
        {
            try
            {
                enrollmentSites = Collections.singleton(StudyManager.getInstance().getSite(getContainer(), _enrollmentSiteId));
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        for (Site site : enrollmentSites)
        {
            String label = site != null ? site.getLabel() : "[No enrollment site assigned]";
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            if (site != null)
            {
                filter.addWhereClause("Ptid IN (SELECT ParticipantId FROM study.Participant " +
                        "WHERE EnrollmentSiteId = ? AND Container = ?)", new Object[] { site.getRowId(), getContainer().getId() }, "Ptid");
            }
            else
            {
                filter.addWhereClause("Ptid IN (SELECT ParticipantId FROM study.Participant " +
                        "WHERE EnrollmentSiteId IS NULL AND Container = ?)", new Object[] { getContainer().getId() }, "Ptid");
            }
            reports.add(new ParticipantSiteReport(label, visits, filter, this));
        }
        return reports;
    }

    public boolean allowsParticipantAggregegates()
    {
        return false;
    }

    public List<String> getAdditionalFormInputHtml()
    {
        List<String> inputs = new ArrayList<String>(super.getAdditionalFormInputHtml());
        Set<Site> sites = SampleManager.getInstance().getEnrollmentSitesWithSpecimens(getContainer());
        inputs.add(getEnrollmentSitePicker("enrollmentSiteId", sites, _enrollmentSiteId));
        return inputs;
    }

    public Integer getEnrollmentSiteId()
    {
        return _enrollmentSiteId;
    }

    public void setEnrollmentSiteId(Integer enrollmentSiteId)
    {
        _enrollmentSiteId = enrollmentSiteId;
    }

    public String getLabel()
    {
        return "By Enrollment Site";
    }

    public Class<? extends SpringSpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpringSpecimenController.ParticipantSiteReportAction.class;
    }
}