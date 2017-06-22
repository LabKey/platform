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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("codemirror");
        dependencies.add("pipeline/AnalyzeForm.js");
    }
%>
<%
    ActionURL cancelURL = urlProvider(PipelineUrls.class).urlBrowse(getContainer(), null);
%>

<labkey:errors />

<script type="text/javascript">

    var selectedFileNames;
    var taskId = LABKEY.ActionURL.getParameter("taskId");
    var path = LABKEY.ActionURL.getParameter("path");
    var analyzeFormCmp;

    function startAnalysis() {
        var protocolName = analyzeFormCmp.getElementById("protocolNameInput").value;
        if (!protocolName) {
            alert("Protocol name is required.");
            analyzeFormCmp.getElementById("protocolNameInput").focus();
            return;
        }

        var config = {
            taskId: taskId,
            path: path,
            files: selectedFileNames,
            saveProtocol: analyzeFormCmp.getElementById("saveProtocolInput").checked,
            protocolName: protocolName,
            successCallback: function() { window.location = LABKEY.ActionURL.buildURL("project", "start.view") }
        };

        if (analyzeFormCmp.getElementById("protocolSelect").selectedIndex == 0) {
            config.protocolDescription = analyzeFormCmp.getElementById("protocolDescriptionInput").value;
            config.xmlParameters = analyzeFormCmp.getXmlParametersValue();
        }

        LABKEY.Pipeline.startAnalysis(config);
    }

    Ext4.onReady(function() {
        selectedFileNames = LABKEY.ActionURL.getParameterArray("file");
        if (!selectedFileNames || selectedFileNames.length == 0) {
            alert("No files have been selected for analysis. Return to the pipeline to select them.");
            window.location = "<%= cancelURL %>";
        }
        else {
            analyzeFormCmp = Ext4.create('LABKEY.pipeline.AnalyzeForm', {
                renderTo: 'pipeline-analyze-form',
                taskId: taskId,
                path: path,
                fileNames: selectedFileNames,
                listeners: {
                    showFileStatus: function(view, submitType) {
                        if (!submitType) {
                            document.getElementById("submitButton").style.display = "none";
                        }
                        else {
                            document.getElementById("submitButton").innerHTML = submitType;
                            document.getElementById("submitButton").style.display = "";
                        }
                    }
                }
            });
        }
    });
</script>

Choose an existing protocol or define a new one.<br />
<labkey:form id="analysis_form">
    <div id="pipeline-analyze-form"></div>
    <labkey:button text="Analyze" id="submitButton" onclick="startAnalysis(); return false;" />
    <labkey:button text="Cancel" href="<%= cancelURL %>"/>
</labkey:form>
