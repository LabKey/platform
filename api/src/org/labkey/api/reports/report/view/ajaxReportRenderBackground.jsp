<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RReport> me = (JspView<RReport>) HttpView.currentView();
    RReport bean = me.getModelBean();
    ViewContext context = getViewContext();

    // TODO: uniqueid
    // TODO: wrap javascript in anonymous function
    // TOOD: disable start button?
    // TODO: Fix these URLs
    ActionURL startReportURL = PageFlowUtil.urlProvider(QueryUrls.class).urlStartBackgroundRReport(context.cloneActionURL(), String.valueOf(bean.getReportId()));
%>
<script type="text/javascript">
    function startJob()
    {
        Ext4.Ajax.request({
            url: <%=q(startReportURL.getLocalURIString())%>,
            method: 'GET',
            success: function(resp) {
                var outputs = Ext4.dom.Query.select('table[@class=labkey-output]');
                if (outputs && outputs.length > 0)
                {
                    for (var i=0; i < outputs.length; i++)
                        outputs[i].style.display = 'none';
                }
                var msgbox = Ext4.create('Ext.window.Window', {
                    html     : '<span class="labkey-message">A pipeline job has been successfully queued.</span>',
                    modal    : false,
                    title    : 'Start Pipeline Job',
                    closable : false,
                    //width    : 300,
                    height   : 100
                });
                msgbox.show();
                msgbox.getEl().fadeOut({duration : 3000, callback : function(){
                    msgbox.hide();
                }});
                Ext4.dom.Query.selectNode('div[@class=start-job-success]').style.display = '';
            },
            failure: startJobFailure
        });
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

</script>

<div id="reportPanel"></div>

<div id="container"></div>
<table>
    <tr class="labkey-wp-header"><th colspan="2" align="left">R Background Job Status</th></tr>
    <tr><td colspan="2"><i>Your R script is configured to run in the background as a pipeline job. You can check status
        on a running job <a href="<%=PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer())%>">here</a> or start a new background job. If the job has been completed, the results will be shown on this
        page. You can also view the report results from the pipeline job status page by clicking on the 'data' button. When you click on the start button, a new background job will be launched.
    </i></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr><td><%= button("Start Job").href("javascript:void(0)").onClick("javascript:startJob()") %></td></tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr><td colspan="2"><div class="start-job-success" style="display: none">The pipeline job has been successfully queued, to view the status <a href="<%=PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer())%>">click here</a>.
        </div></td></tr>
</table>
