<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%
org.labkey.api.module.ModuleLoader loader = org.labkey.api.module.ModuleLoader.getInstance();

String verb = loader.isNewInstall() ? "Install" : "Upgrade";
String verbing = loader.isNewInstall() ? "Installing" : "Upgrading";
%>
<script type="text/javascript">
    try
    {
        LABKEY.requiresExt4Sandbox(true);
    }
    catch (err)
    {
        // In cases where the upgrade/install has gone bad, the server may not be sending down JS files any more
        // so we have to handle cases when we can't use the Client API or ExtJS
        handleException(err);        
    }
</script>

<br/>

<div>This step will <%= verb.toLowerCase() %> the modules needed to use LabKey Server.</div>
<div>Please wait, this page will automatically update with progress information.</div>
<br/>
<div id="progressBarDiv"></div>

<script type="text/javascript">
    try
    {

        var progressBar = Ext4.create('Ext.ProgressBar',
        {
            renderTo: 'progressBarDiv',
            width: 500,
            text: 'Checking status...'
        });


        var task = new Ext.util.DelayedTask(requestProgress);

        requestProgress();
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
        var currentModule = o.currentModule;
        var message;
        if (o.upgradeInProgress)
        {
            message = "<%= verbing %>";
            if (currentModule)
            {
                message += " " + currentModule + " module";
            }
            else
            {
                message += "...";
            }
        }
        else
        {
            message = "<%= verb %>" + " complete";
        }
        
        var runningModules = 0;
        for (var i = 0; i < o.modules.length; i++)
        {
            var module = o.modules[i];
            if (module.state == 'Running' || module.state == 'ReadyToRun')
            {
                runningModules++;
            }
            if (module.name == currentModule)
            {
                if (module.scripts && module.scripts.length > 0)
                {
                    message += " (" + module.scripts[0].description + ")";
                }
            }
        }
        
        progressBar.updateProgress(runningModules / o.modules.length, message);
        if (!o.startupComplete)
        {
            task.delay(500);
        }
        else
        {
            Ext4.get("completeDiv").setStyle('visibility', 'visible');
            Ext4.get("staticStatus").dom.innerHTML = '';
        }
    }
</script>
<br/>
<div id="completeDiv" style="visibility: hidden">
    <%--Could send install/upgrade paths to different pages for settings, but for now use the same simple one for both --%>
    <%= PageFlowUtil.generateButton("Next", new ActionURL(AdminController.NewInstallSiteSettingsAction.class, org.labkey.api.data.ContainerManager.getRoot())) %>
</div>
