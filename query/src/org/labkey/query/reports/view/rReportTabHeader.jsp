<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.reports.report.view.RReportBean"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<RReportBean> me = (JspView<RReportBean>) HttpView.currentView();
    RReportBean bean = me.getModelBean();

    String currentTab = StringUtils.defaultString(bean.getTabId(), ReportsController.TAB_VIEW);
    String nextTab = ReportsController.TAB_SOURCE.equals(currentTab) ? ReportsController.TAB_VIEW : ReportsController.TAB_SOURCE;
%>
<form id="renderReport" action="renderRReport.view" method="post">
    <input type="hidden" name="tabId" value="<%=nextTab%>">
<%
    if (ReportsController.TAB_SOURCE.equals(nextTab))
    {
        for (Pair<String, String> param : bean.getParameters())
            out.write("<input type=\"hidden\" name=\"" + param.getKey() + "\" value=\"" + h(param.getValue()) + "\">");
    }
%>
    <table class="labkey-tab-strip">
        <tr>
            <td class="labkey-tab-space">
                <img src="<%=getWebappURL("_.gif")%>" height=1 width=5>
            </td>
            <td class=<%=ReportsController.TAB_SOURCE.equals(currentTab) ? "labkey-tab-selected" : "labkey-tab-inactive"%>><a <%=ReportsController.TAB_SOURCE.equals(currentTab) ? "" : "href=\"javascript:document.forms[0].submit()\""%>>Source&nbsp;</a></td>
            <td class=<%=ReportsController.TAB_VIEW.equals(currentTab) ? "labkey-tab-selected" : "labkey-tab-inactive"%>><a <%=ReportsController.TAB_VIEW.equals(currentTab) ? "" : "href=\"javascript:document.forms[0].submit()\""%>>Report&nbsp;</a></td>
            <td class="labkey-tab-space" style="text-align:right;" width=100%>
                <img src="<%=getWebappURL("_.gif")%>" height=1 width=5>
            </td>
        </tr>
        <tr>
            <td colspan="4" class="labkey-tab" style="border-top:none;text-align:left;" width=100%>
                <img src="<%=getWebappURL("_.gif")%>" height=1 width=5>
    
