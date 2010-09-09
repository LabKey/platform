<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.query.CustomView"%>
<%@ page import="org.labkey.api.query.QueryDefinition"%>
<%@ page import="org.labkey.api.query.QueryService"%>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.RunReportView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>) HttpView.currentView();
    ScriptReportBean form = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    String reportName = form.getReportName();
    String description = form.getReportDescription();
    if (description != null)
    {
        reportName = reportName + "&nbsp;(" + description + ")";
    }
    final String DEFAULT_VIEW = "Default Grid View";
    String viewName = form.getViewName();
    viewName = viewName != null ? viewName : "";

    boolean isReportInherited = (Boolean)context.getRequest().getAttribute("isReportInherited");

    QueryDefinition queryDef = QueryService.get().getQueryDef(context.getUser(), context.getContainer(),
            form.getSchemaName(),
            form.getQueryName());
    if (queryDef == null)
    {
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), form.getSchemaName());
        if (schema != null)
            queryDef = schema.getQueryDefForTable(form.getQueryName());
    }
    Map<String, String> viewMap = new HashMap<String, String>();
    if (queryDef != null)
    {
        Map<String, CustomView> customViews = queryDef.getCustomViews(context.getUser(), HttpView.currentRequest());
        for (CustomView view : customViews.values())
        {
            if (view.getName() != null)
                viewMap.put(view.getName(), view.getName());
        }
    }
%>
<script type="text/javascript">
    var switchQueryRedirect = "<%=HttpView.currentContext().cloneActionURL().replaceParameter(RunReportView.CACHE_PARAM, String.valueOf(form.getReportId()))%>";

    function switchQueryView()
    {
        LABKEY.setSubmit(true);
        var form = document.getElementById('reportHeader');
        var length = form.elements.length;
        var viewName = byId("viewName").value
        var pairs = [];
        var regexp = /%20/g;

        // urlencode the form data for the post
        for (var i=0; i < length; i++)
        {
            var e = form.elements[i];
            if (e.value)
            {
                var pair = encodeURIComponent(e.name).replace(regexp, "+") + '=' +
                           encodeURIComponent(e.value).replace(regexp, "+");
                pairs.push(pair);
            }
        }
        var ajax = new AJAXSwitchQueryView(pairs.join('&'));
        ajax.send();
    }

    function AJAXSwitchQueryView(viewName)
    {
        var viewName = viewName;
        var req = init();
        req.onreadystatechange = processRequest;

        function init()
        {
            if (window.XMLHttpRequest)
                return new XMLHttpRequest();
            else if (window.ActiveXObject)
                return new ActiveXObject("Microsoft.XMLHTTP");
        }

        this.send = function()
        {
            req.open("POST", "<%=PageFlowUtil.urlProvider(ReportUrls.class).urlUpdateRReportState(HttpView.currentContext().getContainer()).getLocalURIString()%>");
            req.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            req.send(viewName);
        }

        function processRequest()
        {
            if (req.readyState == 4 && req.status == 200)
            {
                if (switchQueryRedirect)
                    window.location = switchQueryRedirect;
            }
        }
    }

    function showViewPicker()
    {
        byId("viewPicker").style.display = "";
    }

</script>


<form id="reportHeader" method="post">
<table width="100%"><tr class="labkey-wp-header"><td align="left">
    <table>
        <tr><td style="border:none;font-weight:bold">View :</td><td style="border:none">&nbsp;<%=reportName%></td></tr>
    <%  if (isReportInherited) { %>
        <tr><td style="border:none;font-weight:bold">Inherited from project :</td><td style="border:none">&nbsp;<%=form.getReport().getDescriptor().getContainerPath()%></td></tr>
    <%  }
        if (!viewMap.isEmpty()) { %>
        <tr><td style="border:none;font-weight:bold">Created from Grid View :</td><td style="border:none">&nbsp;<%=StringUtils.isEmpty(viewName) ? DEFAULT_VIEW : viewName%>&nbsp;[<a href="javascript:void(0)" onclick="javascript:showViewPicker()">change</a>]</td></tr>
    <%  } else { %>
        <tr><td style="border:none;font-weight:bold">Created from Grid View :</td><td style="border:none">&nbsp;<%=StringUtils.isEmpty(viewName) ? DEFAULT_VIEW : viewName%></td></tr>
    <%  }
        if (!viewMap.isEmpty())
        {
            viewMap.put("", DEFAULT_VIEW);
    %>
        <tr id="viewPicker" style="display:none"><td/><td style="border:none"><select id="viewName" name="viewName" onchange="switchQueryView()"><labkey:options value="<%=viewName%>" map="<%=viewMap%>" /></select></td></tr>
    <%  } %>
    </table></td></tr>
    <tr><td/></tr>

    <input type="hidden" name="<%=ReportDescriptor.Prop.reportId.name()%>" value="<%=form.getReportId()%>">
</table></form>
