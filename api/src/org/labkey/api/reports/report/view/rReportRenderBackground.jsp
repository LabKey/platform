<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineJob" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusFile" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.RReportJob" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.io.File" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<RReport> me = (JspView<RReport>) HttpView.currentView();
    RReport bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    File logFile = new File(bean.getReportDir(), RReportJob.LOG_FILE_NAME);
    PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile.getAbsolutePath());
    boolean autoRefresh = statusFile != null &&
            (statusFile.getStatus().equals(PipelineJob.WAITING_STATUS) || statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS));
%>

<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("connection");</script>
<script type="text/javascript">
    function startJob()
    {
        LABKEY.setSubmit(true);

        var url = "<%=context.cloneActionURL().
                setPageFlow("reports").
                setAction("startBackgroundRReport").
                replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId())).
                getLocalURIString()%>";
        YAHOO.util.Connect.asyncRequest("GET", url, {success : postProcess, failure: handleFailure});
    }

    function init()
    {
<%
        if (autoRefresh) {
%>
            window.setInterval("switchTab('<%=context.cloneActionURL().replaceParameter("tabId", "View")%>')", 4000);
<%
        }
%>
    }

	var handleFailure = function(o)
    {
        var div = document.getElementById('container');
	    if(o.responseText !== undefined)
        {
            var msg = "<font class=\"labkey-error\">"
            msg += "An error occurred trying to start the job";
	        msg += "<li>HTTP status: " + o.status + "</li>";

            var status = eval("(" + o.responseText + ')');
            if (status)
                msg += "<li>Message: " + status.exception + "</li>";
            msg += "</font>";
            div.innerHTML = msg;
        }
	}

    function postProcess(o)
    {
        switchTab('<%=context.cloneActionURL().replaceParameter("tabId", "View")%>');
    }
    YAHOO.util.Event.addListener(window, "load", init);

</script>

<div id="container"></div>
<table>
    <tr class="labkey-wp-header"><th colspan="2" align="left">R Background Job Status</th></tr>
    <tr><td colspan="2"><i>Your R script is configured to run in the background as a pipeline job. You can check status
        on a running job or start a new background job. If the job has been completed, the results will be shown on this
        page. When you click on the start button, a new background job will be launched, and the page will automatically
        refresh until the job is complete.
    </i></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
<%
    if (statusFile != null)
    {
%>
    <tr><td class="labkey-form-label">Description</td><td><%=statusFile.getDescription()%></td></tr>
    <tr><td class="labkey-form-label">Status</td><td><%=statusFile.getStatus()%></td></tr>
    <tr><td class="labkey-form-label">Email</td><td><%=statusFile.getEmail()%></td></tr>
    <tr><td class="labkey-form-label">Info</td><td><%=StringUtils.defaultString(statusFile.getInfo(), "")%></td></tr>
<%
    }
    else
    {
%>
    <tr><td class="labkey-form-label">Status</td><td>Not Run</td></tr>
<%
    }

    if (!autoRefresh) {
%>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr><td><a href="javascript:void(0)" onclick="javascript:startJob()"><%=PageFlowUtil.buttonImg("Start Job")%></a></td></tr>
<%  } %>
</table>
