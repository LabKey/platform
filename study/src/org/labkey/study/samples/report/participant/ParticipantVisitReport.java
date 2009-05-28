package org.labkey.study.samples.report.participant;

import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.view.ActionURL;

import java.util.*;
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
 * Created: Jan 29, 2008 4:52:26 PM
 */
public class ParticipantVisitReport extends SpecimenVisitReport<SampleManager.SummaryByVisitParticipant>
{
    boolean _showCohorts;
    public ParticipantVisitReport(String titlePrefix, VisitImpl[] visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        super(titlePrefix, visits, filter, parameters);
        _showCohorts = StudyManager.getInstance().showCohorts(_container, getUser());
    }

    public Collection<Row> createRows()
    {
        try
        {
            SampleManager.SummaryByVisitParticipant[] countSummary =
                    SampleManager.getInstance().getParticipantSummaryByVisitType(_container, getUser(), _filter, getBaseCustomView());
            Map<String, Row> rows = new TreeMap<String, Row>();
            for (SampleManager.SummaryByVisitParticipant count : countSummary)
            {
                String cohort = _showCohorts ? count.getCohort() : "[cohort blinded]";
                if (cohort == null || cohort.length() == 0)
                    cohort = "[No cohort assigned]";
                String key = cohort + "/" + count.getParticipantId();
                Row row = rows.get(key);
                if (row == null)
                {
                    String[] titleHierarchy;
                    if (_showCohorts)
                        titleHierarchy = new String[] { cohort, count.getParticipantId() };
                    else
                        titleHierarchy = new String[] { count.getParticipantId() };
                    row = new Row(titleHierarchy);
                    rows.put(key, row);
                }
                setVisitAsNonEmpty(count.getSequenceNum());
                row.add(count);
            }
            return rows.values();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public int getLabelDepth()
    {
        return _showCohorts ? 2 : 1;
    }

    protected String[] getCellExcelText(VisitImpl visit, SampleManager.SummaryByVisitParticipant summary)
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

    protected String getCellHtml(VisitImpl visit, SampleManager.SummaryByVisitParticipant summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return "&nbsp;";
        ActionURL link = new ActionURL(SpringSpecimenController.SamplesAction.class, _container);
        link.addParameter(SpringSpecimenController.SampleViewTypeForm.PARAMS.showVials, Boolean.TRUE.toString());
        link = updateURLFilterParameter(link, "SpecimenDetail.Visit/SequenceNumMin", visit.getSequenceNumMin());
        link = updateURLFilterParameter(link, "SpecimenDetail.ParticipantId", summary.getParticipantId());
        String linkHtml = link.getLocalURIString();
        if (_filter != null)
        {
            String filterQueryString = getFilterQueryString(visit, summary);
            if (filterQueryString != null && filterQueryString.length() > 0)
                linkHtml += "&" + getFilterQueryString(visit, summary);
        }
        return buildCellHtml(visit, summary, linkHtml);
    }
}
