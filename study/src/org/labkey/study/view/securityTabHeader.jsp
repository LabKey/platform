<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SecurityController.PermissionsForm> me = (JspView<SecurityController.PermissionsForm>) HttpView.currentView();
    SecurityController.PermissionsForm bean = me.getModelBean();

    ActionURL url =  getViewContext().cloneActionURL();
    String currentTab = StringUtils.defaultString(bean.getTabId(), SecurityController.TAB_REPORT);
//    String nextTab = SecurityController.TAB_REPORT.equals(currentTab) ? SecurityController.TAB_STUDY : SecurityController.TAB_REPORT;
%>
    <input type="hidden" name="reportId" value="<%=bean.getReportId()%>">
    <table class="labkey-tab-strip">
        <tr>
            <td class="labkey-tab-space">
                <img src="<%=getWebappURL("_.gif")%>" height=1 width=5>
            </td>
            <td class=<%=SecurityController.TAB_REPORT.equals(currentTab) ? "labkey-tab-selected" : "labkey-tab-inactive"%>><a <%=SecurityController.TAB_REPORT.equals(currentTab) ? "" : "href=\"" + url.replaceParameter("tabId", SecurityController.TAB_REPORT) + "\""%>>Permissions&nbsp;</a></td>
            <td class=<%=SecurityController.TAB_STUDY.equals(currentTab) ? "labkey-tab-selected" : "labkey-tab-inactive"%>><a <%=SecurityController.TAB_STUDY.equals(currentTab) ? "" : "href=\"" + url.replaceParameter("tabId", SecurityController.TAB_STUDY) + "\""%>>Study&nbsp;Security&nbsp;</a></td>
            <td class="labkey-tab-space" style="text-align:right;" width=100%>
                <img src="<%=getWebappURL("_.gif")%>" height=1 width=5>
            </td>
        </tr>
        <tr>
            <td colspan="4" class="labkey-tab" style="border-top:none;text-align:left;" width=100%>
                <img src="<%=getWebappURL("_.gif")%>" height=1 width=5>

