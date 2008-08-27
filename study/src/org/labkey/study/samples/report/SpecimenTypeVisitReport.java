package org.labkey.study.samples.report;

import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.model.Visit;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.util.PageFlowUtil;

import java.util.Map;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Iterator;
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
* Created: Jan 14, 2008 9:44:25 AM
*/
public class SpecimenTypeVisitReport extends SpecimenVisitReport<SampleManager.SummaryByVisitType>
{
    public SpecimenTypeVisitReport(String titlePrefix, Visit[] visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        super(titlePrefix + getTitleSuffix(parameters), visits, filter, parameters);
    }

    public Collection<Row> createRows()
    {
        try
        {
            SampleManager.SpecimenTypeLevel level = _parameters.getTypeLevelEnum();
            SampleManager.SummaryByVisitType[] countSummary =
                    SampleManager.getInstance().getSpecimenSummaryByVisitType(_container, _filter, _parameters.isViewPtidList(), level);
            Map<String, Row> rows = new TreeMap<String, Row>();
            for (SampleManager.SummaryByVisitType count : countSummary)
            {
                String key = count.getPrimaryType() + "/" +
                        (count.getDerivative() != null ? count.getDerivative() : "All") + "/" +
                        (count.getAdditive() != null ? count.getAdditive() : "All");
                Row row = rows.get(key);
                if (row == null)
                {
                    String[] titleHierarchy = level.getTitleHirarchy(count);
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

    private String getCellSummaryText(SampleManager.SummaryByVisitType summary)
    {
        StringBuilder summaryString = new StringBuilder();
        if (_parameters.isViewVialCount())
            summaryString.append(summary.getVialCount());
        if (_parameters.isViewParticipantCount())
        {
            if (summaryString.length() > 0)
                summaryString.append("/");
            summaryString.append(summary.getParticipantCount());
        }
        if (_parameters.isViewVolume())
        {
            if (summaryString.length() > 0)
                summaryString.append("/");
            summaryString.append(summary.getTotalVolume());
        }
        return summaryString.toString();
    }

    protected String[] getCellExcelText(Visit visit, SampleManager.SummaryByVisitType summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return new String[] {};
        String summaryText = getCellSummaryText(summary);
        boolean hasSummaryText = summaryText != null && summaryText.length() > 0;
        int ptidCount = _parameters.isViewPtidList() ? summary.getParticipantIds().size() : 0;
        String[] strArray = new String[ptidCount + (hasSummaryText ? 1 : 0)];
        if (hasSummaryText)
            strArray[0] = summaryText;
        if (ptidCount > 0)
        {
            int currentIndex = (hasSummaryText ? 1 : 0);
            for (String s : summary.getParticipantIds())
                strArray[currentIndex++] = s;
        }
        return strArray;
    }

    protected String getCellHtml(Visit visit, SampleManager.SummaryByVisitType summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return "&nbsp;";
        ActionURL link = new ActionURL(SpringSpecimenController.SamplesAction.class, _container);
        link.addParameter(SpringSpecimenController.SampleViewTypeForm.PARAMS.showVials, Boolean.TRUE.toString());
        link = updateURLFilterParameter(link, "SpecimenDetail.Visit/SequenceNumMin", visit.getSequenceNumMin());

        link = updateURLFilterParameter(link, "SpecimenDetail.PrimaryType/Description", summary.getPrimaryType());
        SampleManager.SpecimenTypeLevel level = _parameters.getTypeLevelEnum();
        if (level == SampleManager.SpecimenTypeLevel.Derivative || level == SampleManager.SpecimenTypeLevel.Additive)
            link = updateURLFilterParameter(link, "SpecimenDetail.DerivativeType/Description", summary.getDerivative());
        if (level == SampleManager.SpecimenTypeLevel.Additive)
            link = updateURLFilterParameter(link, "SpecimenDetail.AdditiveType/Description", summary.getAdditive());

        String linkHtml = link.getLocalURIString();
        if (_filter != null)
            linkHtml += "&" + getFilterQueryString(visit, summary);
        String summaryString = getCellSummaryText(summary);
        StringBuilder cellHtml = new StringBuilder();
        if (summaryString.length() > 0)
        {
            cellHtml.append("<a href=\"").append(linkHtml).append("\">");
            cellHtml.append(summaryString).append("</a>");
        }

        if (_parameters.isViewPtidList())
        {
            if (cellHtml.length() > 0)
                cellHtml.append("<br>");
            if (summary.getParticipantIds() != null)
            {
                for (Iterator<String> it = summary.getParticipantIds().iterator(); it.hasNext();)
                {
                    String participantId = it.next();
                    ActionURL url = new ActionURL(SpringSpecimenController.TypeParticipantReportAction.class, _parameters.getContainer());
                    url.addParameter("participantId", participantId);
                    url.addParameter(SpecimenVisitReportParameters.PARAMS.typeLevel, _parameters.getTypeLevel());
                    url.addParameter(SpecimenVisitReportParameters.PARAMS.statusFilterName, _parameters.getStatusFilterName());
                    url.addParameter(SpecimenVisitReportParameters.PARAMS.viewVialCount, Boolean.TRUE.toString());
                    cellHtml.append("<a href=\"").append(url.getLocalURIString()).append("\">");
                    cellHtml.append(PageFlowUtil.filter(participantId));
                    cellHtml.append("</a>");
                    if (it.hasNext())
                        cellHtml.append("<br>");
                }
            }
        }
        return cellHtml.toString();
    }
}
