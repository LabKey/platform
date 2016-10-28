<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<!-- saveReportView.jsp -->

<%
    JspView<ReportsController.SaveReportViewForm> me = (JspView<ReportsController.SaveReportViewForm>) HttpView.currentView();
    ReportsController.SaveReportViewForm bean = me.getModelBean();
    ViewContext context = getViewContext();

    Report report = bean.getReport(context);
    boolean confirm = bean.getConfirmed() != null ? Boolean.parseBoolean(bean.getConfirmed()) : false;
%>

<script type="text/javascript">

    function validateForm()
    {
        var reportName = document.querySelector('#reportName');
        if ((!reportName) || (reportName.value.length === 0))
        {
            alert("View name cannot be blank.");
            return false;
        }
        return true;
    }
</script>

<table>
<%
    if (bean.getErrors() != null)
    {
        for (ObjectError e : bean.getErrors().getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(context.getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<labkey:form method="post" action="<%=new ActionURL(ReportsController.SaveReportViewAction.class, getContainer())%>" onsubmit="return validateForm();">
    <input type="hidden" name="<%=QueryParam.schemaName%>" value="<%=StringUtils.trimToEmpty(bean.getSchemaName())%>">
    <input type="hidden" name="<%=QueryParam.queryName%>" value="<%=StringUtils.trimToEmpty(bean.getQueryName())%>">
    <input type="hidden" name="<%=QueryParam.viewName%>" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
    <input type="hidden" name="redirectUrl" value="<%=bean.getRedirectUrl()%>">
    <table>
    <tr>
<%
    if (confirm)
    {
%>
        <td>There is already a report called: <i><%=report.getDescriptor().getReportName()%></i>.<br/>Overwrite the existing report?
        <input type=hidden name=confirmed value=1>
        <input type=hidden name=label value="<%=bean.getLabel()%>">
<%
    } else {
%>
        <td><b>Save Report</b></td>
        <td>Name:&nbsp;<input id="reportName" name="label" value="<%=h(bean.getLabel())%>">
        <input type=hidden name=srcURL value="<%=getActionURL().getLocalURIString()%>">
<%
    }
%>
        <input type=hidden name=reportType value="<%=report.getDescriptor().getReportType()%>">
        <input type=hidden name=params value="<%=h(bean.getParams())%>"></td>

<%--
        <td>Add as Custom View For:
            <select id="datasetSelection" name="showWithDataset">
<%
        for (Dataset def : defs)
        {
            if (def.canRead(getUser())) %>
                <option<%=selected(def.getDatasetId() == showWithDataset)%> value="<%=def.getDatasetId()%>"><%=h(def.getLabel())%></option>
<%
        }
%>
            </select>
        </td>
--%>
        <td>&nbsp;</td>
        <td><%= button(confirm ? "Overwrite" : "Save").submit(true) %>
<%
    if (confirm)
    {
%>
        &nbsp;<%= button("Cancel").href(bean.getReturnActionURL()) %>
<%
    } 
%>
    </td>
    </tr>
<%
    if (context.hasPermission(AdminPermission.class)) {
%>
        <tr>
            <td><input type="checkbox" value="true" name="shareReport"<%=checked(bean.getShareReport())%>>Make this report available to all users.</td>
            <td colspan=2>description:<textarea name="description" style="width: 100%;" rows="2"><%=StringUtils.trimToEmpty(bean.getDescription())%></textarea></td>
        </tr>
<%
    } else {
%>
        <tr>
            <td></td>
            <td colspan=2>description:&nbsp;<textarea name="description" style="width: 100%;" rows="2"><%=StringUtils.trimToEmpty(bean.getDescription())%></textarea></td>
        </tr>
<%
    }
%>
    </table>
</labkey:form><hr/>
