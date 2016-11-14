<%
/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
    }
%>
<%
    ActionURL cancelURL = (ActionURL)HttpView.currentModel();
%>

<labkey:errors />

<script type="text/javascript">

    var allProtocols;
    var selectedFileNames;
    var taskId = LABKEY.ActionURL.getParameter("taskId");
    var path = LABKEY.ActionURL.getParameter("path");

    function getProtocolsCallback(protocols, defaultProtocolName)
    {
        var selectElement = document.getElementById("protocolSelect");
        selectElement.options[0].text = "<New Protocol>";
        allProtocols = {};
        var defaultProtocolIndex = -1;
        for (var i = 0; i < protocols.length; i++)
        {
            selectElement.options[i + 1] = new Option(protocols[i].name, protocols[i].name, protocols[i].name == defaultProtocolName);
            allProtocols[protocols[i].name] = protocols[i];
            if (protocols[i].name == defaultProtocolName)
                defaultProtocolIndex = i + 1;
        }
        if (changeProtocol(defaultProtocolName))
        {
            selectElement.selectedIndex = defaultProtocolIndex;
            selectElement.focus();
        }
        else
        {
            document.getElementById("protocolNameInput").focus();
        }
    }

    function startAnalysis()
    {
        var protocolName = document.getElementById("protocolNameInput").value;
        if (!protocolName)
        {
            alert("Protocol name required");
            document.getElementById("protocolNameInput").focus();
            return;
        }

        var config =
        {
            taskId: taskId,
            path: path,
            files: selectedFileNames,
            saveProtocol: document.getElementById("saveProtocolInput").checked,
            protocolName: protocolName,
            successCallback: function() { window.location = LABKEY.ActionURL.buildURL("project", "start.view") }
        };
        if (document.getElementById("protocolSelect").selectedIndex == 0)
        {
            config.protocolDescription = document.getElementById("protocolDescriptionInput").value; 
            config.xmlParameters = this.codeMirror.getValue();
        }
        LABKEY.Pipeline.startAnalysis(config);
    }

    /** @param statusInfo is either a string to be shown for all files, or an array with status information for each file */
    function showFileStatus(statusInfo, submitType)
    {
        var globalStatus = "";
        var files = [];
        if (typeof statusInfo === 'string')
        {
            files = [];
            globalStatus = statusInfo;
        }
        else if (statusInfo && statusInfo.length)
        {
            // Assume it's an array
            files = statusInfo;
        }
        var status = "";
        for (var i = 0; i < selectedFileNames.length; i++)
        {
            status = status + selectedFileNames[i];
            for (var j = 0; j < files.length; j++)
            {
                if (selectedFileNames[i] == files[j].name)
                {
                    if (files[j].status)
                    {
                        status += " <b>(" + files[j].status + ")</b>";
                    }
                    break;
                }
            }
            status += " " + globalStatus + "<br/>";
        }
        document.getElementById("fileStatus").innerHTML = status;
        if (!submitType)
        {
            document.getElementById("submitButton").style.display = "none";
        }
        else
        {
            document.getElementById("submitButton").innerHTML = submitType;
            document.getElementById("submitButton").style.display = "";
        }
    }

    function resetStyles(disabled)
    {
        document.getElementsByClassName("CodeMirror")[0].style.backgroundColor = window.getComputedStyle(document.getElementById("protocolNameInput")).backgroundColor;
        this.codeMirror.setOption("readOnly", disabled);
        document.getElementById("protocolName").style.visibility = disabled ? "hidden" : "visible";
    }

    /** @return true if an existing, saved protocol is selected */
    function changeProtocol(selectedProtocolName)
    {
        var selectedProtocol = allProtocols[selectedProtocolName];
        var inputs = Ext4.DomQuery.select("[@class=protocol-input]");
        var disabledState;
        if (selectedProtocol)
        {
            disabledState = true;
            document.getElementById("protocolNameInput").value = selectedProtocol.name;
            document.getElementById("protocolDescriptionInput").value = selectedProtocol.description;
            this.codeMirror.setValue(selectedProtocol.xmlParameters);
            showFileStatus("<em>(Refreshing status)</em>");
            LABKEY.Pipeline.getFileStatus(
            {
                taskId: taskId,
                path: path,
                files: selectedFileNames,
                successCallback: showFileStatus,
                protocolName: selectedProtocolName
            });
        }
        else
        {
            disabledState = false;
            document.getElementById("protocolNameInput").value = "";
            document.getElementById("protocolDescriptionInput").value = "";
            this.codeMirror.setValue("<?xml version=\"1.0\"?>\n" +
                        "<bioml>\n" +
                            "  <!-- Override default parameters here. Example:-->\n" +
                            "  <!-- <note label=\"myParameterName\" type=\"input\">overrideValue</note>-->\n" +
                        "</bioml>");
            showFileStatus("", "Analyze");
        }
        for (var i = 0; i < inputs.length; i++)
        {
            inputs[i].disabled = disabledState;
        }

        resetStyles(disabledState);
        return disabledState;
    }

    Ext4.onReady(function()
    {
        selectedFileNames = LABKEY.ActionURL.getParameterArray("file");
        if (!selectedFileNames || selectedFileNames.length == 0)
        {
            alert("No files have been selected for analysis. Return to the pipeline to select them.");
            window.location = "<%= urlProvider(PipelineUrls.class).urlBrowse(getContainer(), null) %>";
        }
        showFileStatus("<em>(Refreshing status)</em>");
        LABKEY.Pipeline.getProtocols({ taskId: taskId, successCallback: getProtocolsCallback });
        var el = Ext4.get("xmlParametersInput");
        if (el)
        {
            this.codeMirror = CodeMirror.fromTextArea(el.dom, {
                mode: "xml",
                lineNumbers: true
            });
            LABKEY.codemirror.RegisterEditorInstance('xmlParameters', this.codeMirror);
            document.getElementsByClassName("CodeMirror")[0].style.border = window.getComputedStyle(document.getElementById("protocolDescriptionInput")).border;
        }
    });
</script>

    Choose an existing protocol or define a new one.<br />

<labkey:form id="analysis_form">
<table>
    <tr>
        <td class='labkey-form-label'>Analysis Protocol:</td>
        <td>
            <select id="protocolSelect" name="protocol" onchange="if (!changeProtocol(this.options[this.selectedIndex].value)) {document.getElementById('protocolNameInput').focus();}">
                <option>&lt;Loading...&gt;</option>
            </select>
            <span id="protocolName">
                <label for="protocolNameInput" >&nbsp;Name:</label>
                <input disabled id="protocolNameInput" class="protocol-input" type="text" name="protocolName" size="40" />
            </span>
        </td>
    </tr>
    <tr>
        <td class='labkey-form-label'>Protocol Description:</td>
        <td><textarea disabled id="protocolDescriptionInput" class="protocol-input" style="width: 100%;" name="protocolDescription" cols="150" rows="4"></textarea></td>
    </tr>

    <tr>
        <td class='labkey-form-label'>File(s):</td>
        <td id="fileStatus" />
    </tr>
    <tr id="parametersRow">
        <td class='labkey-form-label'>Parameters:</td>
        <td>
            <textarea id="xmlParametersInput" class="protocol-input" style="width: 100%;" name="xmlParameters" cols="150" rows="15"></textarea>
        </td>
    </tr>
    <tr>
        <td/>
        <td>
            <input type="checkbox" class="protocol-input" disabled id="saveProtocolInput" name="saveProtocol" checked/>
            <label for="saveProtocolInput">Save protocol for future use</label>
        </td>
    </tr>
    <tr>
        <td/>
        <td>
            <labkey:button text="Analyze" id="submitButton" onclick="startAnalysis(); return false;" />
            <labkey:button text="Cancel" href="<%= cancelURL %>"/>
        </td>
    </tr>
</table>
</labkey:form>
