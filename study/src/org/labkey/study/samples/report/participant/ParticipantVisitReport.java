package org.labkey.study.samples.report.participant;

import org.labkey.study.model.Visit;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
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
 * Created: Jan 29, 2008 4:52:26 PM
 */
public class ParticipantVisitReport extends SpecimenVisitReport<SampleManager.SummaryByVisitParticipant>
{
    boolean _showCohorts;
    public ParticipantVisitReport(String title, Visit[] visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        super(title + getTitleSuffix(parameters), visits, filter, parameters);
        _showCohorts = StudyManager.getInstance().showCohorts(_container, _parameters.getUser());
    }

    public Collection<Row> createRows()
    {
        try
        {
            SampleManager.SummaryByVisitParticipant[] countSummary =
                    SampleManager.getInstance().getParticipantSummaryByVisitType(_parameters.getContainer(), _filter);
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

    protected String getCellExcelText(Visit visit, SampleManager.SummaryByVisitParticipant summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return "";

        StringBuilder summaryString = new StringBuilder();
        if (_parameters.isViewVialCount())
            summaryString.append(summary.getVialCount());
        if (_parameters.isViewVolume())
        {
            if (summaryString.length() > 0)
                summaryString.append("/");
            summaryString.append(summary.getTotalVolume());
        }
        return summaryString.toString();
    }

    protected String getCellHtml(Visit visit, SampleManager.SummaryByVisitParticipant summary)
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
        String summaryString = getCellExcelText(visit, summary);
        StringBuilder cellHtml = new StringBuilder();
        if (summaryString.length() > 0)
        {
            cellHtml.append("<a href=\"").append(linkHtml).append("\">");
            cellHtml.append(summaryString).append("</a>");
        }
        return cellHtml.toString();
    }
}
