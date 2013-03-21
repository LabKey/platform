<%
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
%>
<%@ page import="org.labkey.api.pipeline.PipelineJob" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusFile" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.RReportJob" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.io.File" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RReport> me = (JspView<RReport>) HttpView.currentView();
    RReport bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    File logFile = new File(bean.getReportDir(), RReportJob.LOG_FILE_NAME);
    PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile);
    boolean autoRefresh = statusFile != null &&
            (statusFile.getStatus().equals(PipelineJob.WAITING_STATUS) || statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS));

    // TODO: uniqueid
    // TODO: wrap javascript in anonymous function
    // TOOD: disable start button?
    // TODO: Fix these URLs
    ActionURL startReportURL = context.cloneActionURL().
            setController("reports").
                setAction("startBackgroundRReport").
                replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));

    ActionURL getResultsURL = startReportURL.clone().setAction("getBackgroundReportResults");
%>
<script type="text/javascript">
    var timer;

    function startJob()
    {
        Ext.Ajax.request({
            url: <%=q(startReportURL.getLocalURIString())%>,
            method: 'GET',
            success: startJobSuccess,
            failure: startJobFailure
        });
    }

    function startJobSuccess(o)
    {
        init();
    }

	function startJobFailure(o)
    {
        var div = document.getElementById('container');

	    if (o.responseText !== undefined)
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

    function init()
    {
        pollForResults();
    }

<%
    if (autoRefresh)
    { %>
        Ext.onReady(init());
<%
    } %>

    function pollForResults()
    {
        Ext.Ajax.request({
            url: <%=q(getResultsURL.getLocalURIString())%>,
            method: 'GET',
            success: resultsSuccess,
            failure: resultsFailure
        });
    }

    function resultsSuccess(response)
    {
        var map = Ext.util.JSON.decode(response.responseText);

        var extDiv = Ext.get("results");
        extDiv.update(map['results']);

        if (map['status'] == <%=q(PipelineJob.COMPLETE_STATUS)%>)
            stopPolling();
        else if (!timer)
            timer = window.setInterval("pollForResults()", 4000);
    }

    function resultsFailure(o)
    {
        alert("Failure retrieving results: " + o);
        stopPolling();
    }

    function stopPolling()
    {
        if (timer)
        {
            window.clearInterval(timer);
            timer = null;
        }
    }
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
    <tr><td colspan="2">&nbsp;</td></tr><%

    if (!autoRefresh)
    {
%>
    <tr><td><%=generateButton("Start Job", "javascript:void(0)", "javascript:startJob()")%></td></tr>
    <tr><td colspan="2">&nbsp;</td></tr><%
    } %>
</table>
<div id="results"></div>
