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
package org.labkey.specimen.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.study.Visit;
import org.labkey.api.view.ActionURL;
import org.labkey.specimen.actions.SpecimenController;
import org.labkey.specimen.report.RequestSummaryByVisitType;
import org.labkey.specimen.report.SpecimenReportManager;
import org.labkey.specimen.report.SpecimenTypeLevel;
import org.labkey.specimen.report.SpecimenVisitReport;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: brittp
 * Created: Feb 5, 2008 2:17:53 PM
 */
public class RequestParticipantReport extends SpecimenVisitReport<RequestSummaryByVisitType>
{
    private final boolean _completeRequestsOnly;

    public RequestParticipantReport(String titlePrefix, List<? extends Visit> visits, SimpleFilter filter, RequestParticipantReportFactory parameters)
    {
        super(titlePrefix, visits, filter, parameters);
        _completeRequestsOnly = parameters.isCompletedRequestsOnly();
    }

    @Override
    public Collection<Row> createRows()
    {
        SpecimenTypeLevel level = getTypeLevelEnum();
        RequestSummaryByVisitType[] countSummary =
                SpecimenReportManager.get().getRequestSummaryBySite(_container, getUser(), _filter,
                        isViewPtidList(), level, getBaseCustomView(), _completeRequestsOnly);
        Map<String, Row> rows = new TreeMap<>();
        for (RequestSummaryByVisitType count : countSummary)
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

    @Override
    protected String[] getCellExcelText(Visit visit, RequestSummaryByVisitType summary)
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
    protected String getCellHtml(Visit visit, RequestSummaryByVisitType summary)
    {
        if (summary == null || summary.getVialCount() == null)
            return "&nbsp;";
        ActionURL url = SpecimenController.getSpecimensURL(_container, true);
        url = updateURLFilterParameter(url, "SpecimenDetail.Visit/SequenceNumMin", visit.getSequenceNumMin());
        url = updateURLFilterParameter(url, "SpecimenDetail.PrimaryType/Description", summary.getPrimaryType());
        SpecimenTypeLevel level = getTypeLevelEnum();
        if (level == SpecimenTypeLevel.Derivative || level == SpecimenTypeLevel.Additive)
            url = updateURLFilterParameter(url, "SpecimenDetail.DerivativeType/Description", summary.getDerivative());
        if (level == SpecimenTypeLevel.Additive)
            url = updateURLFilterParameter(url, "SpecimenDetail.AdditiveType/Description", summary.getAdditive());
        String linkHtml = url.getLocalURIString();
        if (_filter != null)
            linkHtml += "&" + getFilterQueryString(visit, summary);
        return buildCellHtml(visit, summary, linkHtml);
    }

    @Override
    protected String getFilterQueryString(Visit visit, RequestSummaryByVisitType summary)
    {
        return super.getFilterQueryString(visit, summary)  + "&" +
                (_completeRequestsOnly ? SpecimenQueryView.PARAMS.showCompleteRequestedBySite : SpecimenQueryView.PARAMS.showRequestedBySite)
                + "=" + summary.getDestinationSiteId();
    }
}
