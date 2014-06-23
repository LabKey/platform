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

import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.sql.SQLException;

/**
 * User: brittp
 * Created: Feb 5, 2008 2:17:53 PM
 */
public class RequestParticipantReport extends SpecimenVisitReport<SampleManager.RequestSummaryByVisitType>
{
    private boolean _completeRequestsOnly;
    public RequestParticipantReport(String titlePrefix, List<VisitImpl> visits, SimpleFilter filter, RequestParticipantReportFactory parameters)
    {
        super(titlePrefix, visits, filter, parameters);
        _completeRequestsOnly = parameters.isCompletedRequestsOnly();
    }

    public Collection<Row> createRows()
    {
        try
        {
            SampleManager.SpecimenTypeLevel level = getTypeLevelEnum();
            SampleManager.RequestSummaryByVisitType[] countSummary =
                    SampleManager.getInstance().getRequestSummaryBySite(_container, getUser(), _filter,
                            isViewPtidList(), level, getBaseCustomView(), _completeRequestsOnly);
            Map<String, Row> rows = new TreeMap<>();
            for (SampleManager.RequestSummaryByVisitType count : countSummary)
            {
                String key = count.getSiteLabel() + "/" + count.getPrimaryType() + "/" +
                        (count.getDerivative() != null ? count.getDerivative() : "All") + "/" +
                        (count.getAdditive() != null ? count.getAdditive() : "All");
                Row row = rows.get(key);
                if (row == null)
                {
                    String[] typeHierarchy = level.getTitleHierarchy(count);
                    String[] titleHierarchy = new String[typeHierarchy.length + 1];
                    titleHierarchy[0] = count.getSiteLabel();
                    System.arraycopy(typeHierarchy, 0, titleHierarchy, 1, typeHierarchy.length);
                    row = new Row(titleHierarchy);
                    rows.put(key, row);
                }
                setVisitAsNonEmpty(count.getVisit());
                row.add(count);
            }
            return rows.values();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected String[] getCellExcelText(VisitImpl visit, SampleManager.RequestSummaryByVisitType summary)
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

    protected String getCellHtml(VisitImpl visit, SampleManager.RequestSummaryByVisitType summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return "&nbsp;";
        ActionURL link = SpecimenController.getSamplesURL(_container);
        link.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, Boolean.TRUE.toString());
        link = updateURLFilterParameter(link, "SpecimenDetail.Visit/SequenceNumMin", visit.getSequenceNumMin());

        link = updateURLFilterParameter(link, "SpecimenDetail.PrimaryType/Description", summary.getPrimaryType());
        SampleManager.SpecimenTypeLevel level = getTypeLevelEnum();
        if (level == SampleManager.SpecimenTypeLevel.Derivative || level == SampleManager.SpecimenTypeLevel.Additive)
            link = updateURLFilterParameter(link, "SpecimenDetail.DerivativeType/Description", summary.getDerivative());
        if (level == SampleManager.SpecimenTypeLevel.Additive)
            link = updateURLFilterParameter(link, "SpecimenDetail.AdditiveType/Description", summary.getAdditive());
        String linkHtml = link.getLocalURIString();
        if (_filter != null)
            linkHtml += "&" + getFilterQueryString(visit, summary);
        return buildCellHtml(visit, summary, linkHtml);
    }

    protected String getFilterQueryString(VisitImpl visit, SampleManager.RequestSummaryByVisitType summary)
    {
        return super.getFilterQueryString(visit, summary)  + "&" +
                (_completeRequestsOnly ? SpecimenQueryView.PARAMS.showCompleteRequestedBySite : SpecimenQueryView.PARAMS.showRequestedBySite)
                + "=" + summary.getDestinationSiteId();
    }
}
