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
package org.labkey.study.specimen.report.participant;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.view.ActionURL;
import org.labkey.study.CohortFilter;
import org.labkey.study.SpecimenManager;
import org.labkey.study.SpecimenManager.SummaryByVisitParticipant;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenReportTitle;
import org.labkey.study.specimen.report.SpecimenVisitReport;
import org.labkey.study.specimen.report.SpecimenVisitReportParameters;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: brittp
 * Created: Jan 29, 2008 4:52:26 PM
 */
public class ParticipantVisitReport extends SpecimenVisitReport<SummaryByVisitParticipant>
{
    private final boolean _showCohorts;

    public ParticipantVisitReport(String titlePrefix, List<VisitImpl> visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        super(titlePrefix, visits, filter, parameters);
        _showCohorts = StudyManager.getInstance().showCohorts(_container, getUser());
    }

    @Override
    public Collection<Row> createRows()
    {
        CohortFilter.Type cohortType = getCohortFilter() != null ? getCohortFilter().getType() : CohortFilter.Type.DATA_COLLECTION;
        Collection<SummaryByVisitParticipant> countSummary =
                SpecimenManager.getInstance().getParticipantSummaryByVisitType(_container, getUser(), _filter, getBaseCustomView(), cohortType);
        Map<String, Row> rows = new TreeMap<>();
        for (SummaryByVisitParticipant count : countSummary)
        {
            String cohort = _showCohorts ? count.getCohort() : "[cohort blinded]";
            if (cohort == null || cohort.length() == 0)
                cohort = "[No cohort assigned]";
            String key = cohort + "/" + count.getParticipantId();
            Row row = rows.get(key);
            if (row == null)
            {
                String ptid = count.getParticipantId();
                SpecimenReportTitle ptidTitle = new SpecimenReportTitle(ptid, DemoMode.id(ptid, getContainer(), getUser()));
                SpecimenReportTitle[] titleHierarchy;
                if (_showCohorts)
                    titleHierarchy = new SpecimenReportTitle[] {new SpecimenReportTitle(cohort), ptidTitle};
                else
                    titleHierarchy = new SpecimenReportTitle[] {ptidTitle};
                row = new Row(titleHierarchy);
                rows.put(key, row);
            }
            setVisitAsNonEmpty(count.getVisit());
            row.add(count);
        }
        return rows.values();
    }

    @Override
    public int getLabelDepth()
    {
        return _showCohorts ? 2 : 1;
    }

    @Override
    protected String[] getCellExcelText(VisitImpl visit, SummaryByVisitParticipant summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return new String[] {};

        StringBuilder summaryString = new StringBuilder();
        if (isViewVialCount())
            summaryString.append(summary.getVialCount());
        if (isViewVolume())
        {
            if (summaryString.length() > 0)
                summaryString.append("/");
            summaryString.append(summary.getTotalVolume());
        }
        return new String[] { summaryString.toString() };
    }

    @Override
    protected String getCellHtml(VisitImpl visit, SummaryByVisitParticipant summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return "&nbsp;";
        ActionURL link = SpecimenController.getSamplesURL(_container);
        link.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, Boolean.TRUE.toString());
        link = updateURLFilterParameter(link, "SpecimenDetail.Visit/SequenceNumMin", visit.getSequenceNumMinDouble());
        link = updateURLFilterParameter(link, "SpecimenDetail." + StudyService.get().getSubjectColumnName(getContainer()), summary.getParticipantId());
        String linkHtml = link.getLocalURIString();
        if (_filter != null)
        {
            String filterQueryString = getFilterQueryString(visit, summary);
            if (filterQueryString != null && filterQueryString.length() > 0)
                linkHtml += "&" + filterQueryString;
        }
        return buildCellHtml(visit, summary, linkHtml);
    }
}
