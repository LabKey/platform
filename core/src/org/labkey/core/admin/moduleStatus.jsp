<%
/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
AdminController.ModuleStatusBean bean = (AdminController.ModuleStatusBean)getModelBean();
%>
<br/>

<div>This step will <%= h(bean.verb.toLowerCase()) %> the modules needed to use LabKey Server.</div>
<div>Please wait, this page will automatically update with progress information.</div>
<br/>
<div id="progressBarDiv"></div>

<script type="text/javascript">
    // TODO: Move this all out of global
    try
    {
        Ext4.onReady(function() {

            Ext4.create('Ext.ProgressBar', {
                renderTo: 'progressBarDiv',
                id: 'status-progress',
                width: 500,
                text: 'Checking status...',
                task: new Ext4.util.DelayedTask(requestProgress)
            });

            requestProgress();
        });
    }
    catch (err)
    {
        // In cases where the upgrade/install has gone bad, the server may not be sending down JS files any more
        // so we have to handle cases when we can't use the Client API or ExtJS
        handleException(err);
    }

    function handleException(err)
    {
        // An error usually means that something went wrong with the upgrade/install. Our best bet it to just reload,
        // the page since the server will most likely send back an error instead of the regular UI

        // First check if we're already reloaded once or not
        if (window.location.href.indexOf("reloaded=true") == -1)
        {
            // Remember that we reloaded so that we don't keep refreshing over and over
            var newHref = window.location.href;
            if (newHref.indexOf("?") != -1)
            {
                newHref += "&";
            }
            newHref += "reloaded=true";
            window.location.href = newHref;
        }
        else
        {
            // Give up and just pop up an alert. There's a good chance that the server isn't sending down any
            // other files, so don't try to get fancy with Ext dialogs or anything like that.
            alert("There was an error: " + err);
        }
    }

    function requestProgress()
    {
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL("admin-sql", "getModuleStatus", "/"),
            method: 'POST',
            success: LABKEY.Utils.getCallbackWrapper(updateProgress, this, false),
            failure: LABKEY.Utils.getCallbackWrapper(function() { window.location.reload() }, null, true)
        });
    }

    function updateProgress(o)
    {
        var message;

        <%--
        Once module upgrades are complete, the o.message field will
        be set by the ModuleLoader.getInstance().getStartingUpMessage().
        --%>
        if (o.message)
        {
            message = o.message;
        }
        else
        {
            if (o.upgradeInProgress)
            {
                message = "<%= h(bean.verbing) %>";
                if (o.currentlyUpgradingModule)
                {
                    message += " " + o.currentlyUpgradingModule + " module";
                }
                else
                {
                    message += "...";
                }
            }
            else if (o.startupComplete)
            {
                message = "<%= h(bean.verb) %>" + " complete.";
            }
        }

        var progress = 0;
        for (var i = 0; i < o.modules.length; i++)
        {
            var module = o.modules[i];
            if (module.state == 'ReadyToStart')
            {
                progress += 1;
            }
            else if (module.state == 'Starting')
            {
                progress += 2;
            }
            else if (module.state == 'Started')
            {
                progress += 3;
            }

            if (o.upgradeInProgress && module.name == o.currentlyUpgradingModule)
            {
                if (module.scripts && module.scripts.length > 0)
                {
                    message += " (" + module.scripts[0].description + ")";
                }
            }
        }

        var progressBar = Ext4.getCmp('status-progress');
        if (progressBar) {
            progressBar.updateProgress(progress / (3*o.modules.length), message);
        }

        if (!o.startupComplete)
        {
            (progressBar ? progressBar.task.delay(500) : '');
        }
        else
        {
            Ext4.get("completeDiv").setStyle('visibility', 'visible');
            document.getElementById("nextButton").focus();
        }
    }
</script>
<br/>
<div id="completeDiv" style="visibility: hidden;">
    <%-- Send new installs to set their defaults, and upgrades to the complete page --%>
    <%= PageFlowUtil.button("Next").href(bean.nextURL).attributes("id=\"nextButton\"") %>
</div>
