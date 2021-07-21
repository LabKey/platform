<%
    /*
     * Copyright (c) 2012-2016 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.reports.model.NotificationInfo"%>
<%@ page import="org.labkey.api.reports.model.ViewCategory"%>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.query.reports.view.ReportAndDatasetChangeDigestEmailTemplate" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.audit.AuditUrls" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<ReportAndDatasetChangeDigestEmailTemplate.NotificationBean> me = (JspView<ReportAndDatasetChangeDigestEmailTemplate.NotificationBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    ReportAndDatasetChangeDigestEmailTemplate.NotificationBean bean = me.getModelBean();
%>

<tr>
    <th>&nbsp;&nbsp;</th>
    <th>Name</th>
    <th>Type</th>
    <th>Last Modified</th>
    <th>Status</th>
    <th>Changes</th>
</tr>
<%
    for (Map.Entry<ViewCategory, List<NotificationInfo>> entry : bean.getReports().entrySet())
    {
        // category row
        ViewCategory category = entry.getKey();
        int i=0;
        %>
        <tr>
            <th colspan='6'><%=h("Category '" + category.getLabel() + "'")%></th>
        </tr>
        <%
        for (NotificationInfo info : entry.getValue())
        {
            String rowCls = (i++ % 2 == 0) ? "labkey-row" : "labkey-alternate-row";
            ActionURL url;
            ActionURL auditUrl = null;

            switch (info.getType())
            {
                case dataset -> {
                    url = PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(getContainer(), info.getRowId());
                    auditUrl = PageFlowUtil.urlProvider(AuditUrls.class).getAuditLog(getContainer(), "DatasetAuditEvent",
                            info.getStartDate(), info.getEndDate());
                    Map.Entry<String, String> param = new CompareType.CompareClause(FieldKey.fromParts("datasetid"), CompareType.EQUAL, info.getRowId())
                            .toURLParam(QueryView.DATAREGIONNAME_DEFAULT + ".");
                    auditUrl.addParameter(param.getKey(), param.getValue());
                }
                default -> {
                    url = info.getReport().getRunReportURL(context);
                }
            }
        %>
        <tr class="<%=h(rowCls)%>">
            <td>&nbsp;&nbsp;</td>
            <td><a href="<%=h(url.getURIString())%>"><%=h(info.getName())%></a></td>
            <td><%=h(info.getDescription())%></td>
            <td><%=h(DateUtil.formatDateTime(getContainer(), info.getModified()))%></td>
            <td><%=h(info.getStatus())%></td>
        <%  if (auditUrl != null) { %>
            <td><a href="<%=h(auditUrl.getURIString())%>">view details</a></td>
        <%  } else{ %>
            <td></td>
        <%  } %>
        </tr>
<%
        }
    }
%>
