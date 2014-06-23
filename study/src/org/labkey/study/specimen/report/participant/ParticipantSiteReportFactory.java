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
package org.labkey.study.specimen.report.participant;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenVisitReport;
import org.labkey.study.specimen.report.SpecimenVisitReportParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Created: Jan 30, 2008 3:40:07 PM
 */
public class ParticipantSiteReportFactory extends SpecimenVisitReportParameters
{
    private Integer _enrollmentSiteId;

    protected List<? extends SpecimenVisitReport> createReports()
    {
        List<VisitImpl> visits = SpecimenManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        List<ParticipantVisitReport> reports = new ArrayList<>();
        Set<LocationImpl> enrollmentLocations;
        if (_enrollmentSiteId == null)
        {
            enrollmentLocations = SpecimenManager.getInstance().getEnrollmentSitesWithSpecimens(getContainer(), getUser());
            // add null to the set so we can search for ptid without an enrollment site:
            enrollmentLocations.add(null);
        }
        else if (_enrollmentSiteId == -1)
        {
            enrollmentLocations = Collections.singleton(null);
        }
        else
        {
            enrollmentLocations = Collections.singleton(StudyManager.getInstance().getLocation(getContainer(), _enrollmentSiteId));
        }

        for (LocationImpl location : enrollmentLocations)
        {
            String label = location != null ? location.getLabel() : "[No enrollment location assigned]";
            SimpleFilter filter = new SimpleFilter();
            addBaseFilters(filter);
            if (location != null)
            {
                filter.addWhereClause("" + StudyService.get().getSubjectColumnName(getContainer()) + " IN (SELECT ParticipantId FROM study.Participant " +
                        "WHERE EnrollmentSiteId = ? AND Container = ?)", new Object[] { location.getRowId(), getContainer().getId() }, FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())));
            }
            else
            {
                filter.addWhereClause(StudyService.get().getSubjectColumnName(getContainer()) + " IN (SELECT ParticipantId FROM study.Participant " +
                        "WHERE EnrollmentSiteId IS NULL AND Container = ?)", new Object[] { getContainer().getId() }, FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())));
            }
            reports.add(new ParticipantSiteReport(label, visits, filter, this));
        }
        return reports;
    }

    public boolean allowsParticipantAggregegates()
    {
        return false;
    }

    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        List<Pair<String, String>> inputs = new ArrayList<>(super.getAdditionalFormInputHtml());
        Set<LocationImpl> locations = SpecimenManager.getInstance().getEnrollmentSitesWithSpecimens(getContainer(), getUser());
        // add null to the set so we can search for ptid without an enrollment site:
        locations.add(null);
        inputs.add(getEnrollmentSitePicker("enrollmentSiteId", locations, _enrollmentSiteId));
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
        return StudyService.get().getSubjectNounSingular(getContainer()) + " by Enrollment Location";
    }

    @Override
    public String getReportType()
    {
        return "ParticipantByEnrollmentLocation";
    }

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.ParticipantSiteReportAction.class;
    }
}
