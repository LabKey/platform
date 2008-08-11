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
<%@ page import="org.labkey.api.pipeline.PipelineUrls"%>
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.pipeline.analysis.AnalysisController" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.pipeline.analysis.AnalyzePage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AnalysisController.AnalyzeForm form = getForm();
    PipelineUrls up = urlProvider(PipelineUrls.class);

    boolean hasWork = false;
    boolean hasRun = false;
%>

<labkey:errors />

<span class="labkey-header-large">A file analysis protocol is defined by the set of parameters used to run the commands in the pipeline.</span>
<br><br>
<%
    if(getProtocolNames().length > 0)
        out.print("Choose an existing protocol or define a new one.<br>");
    else
        out.print("Define a new protocol using the following fields and run analysis.<br>");
%>
    <br>

<form id="analysis_form" method="post" action="<%=urlFor(AnalysisController.AnalyzeAction.class)%>">
    <input type="hidden" name="runAnalysis" value="true">
    <input type="hidden" name="path" value="<%=h(form.getPath())%>">
    <input type="hidden" name="nsClass" value="<%=h(form.getNsClass())%>">
    <input type="hidden" name="name" value="<%=h(form.getName())%>">
<table>
    <tr><td class='labkey-form-label'>Analysis Protocol:</td>
        <td><select name="protocol"
                            onchange="changeProtocol(this)">
            <option>&lt;New Protocol&gt;</option>
<%
    for (String protocol : getProtocolNames())
    {
        if (protocol.equals(form.getProtocol()))
            out.print("<option selected>");
        else
            out.print("<option>");
        out.print(h(protocol));
        out.print("</option>");
    }
%>
        </select></td></tr>

<%  if ("".equals(form.getProtocol()))
    { %>
    <tr><td class='labkey-form-label'>Protocol Name:</td>
        <td><input type="text" name="protocolName" size="40" value="<%=h(form.getProtocolName())%>"></td></tr>
    <tr><td class='labkey-form-label'>Protocol Description:</td>
        <td><textarea style="width: 100%;" name="protocolDescription" cols="150" rows="4"><%=h(form.getProtocolDescription())%></textarea></td></tr>
<%  } %>

<%  if (form.getFileInputNames().length != 1)
    { %>
    <tr><td class='labkey-form-label'>Analyze Files:</td>
<%  }
    else
    { %>
    <tr><td class='labkey-form-label'>Analyze File:</td>
<%  } %>
        <td>
<%
    if (form.getFileInputNames().length == 0)
        out.print("No files found");
    else
    {
        hasWork = !form.isActiveJobs(); %>
        <table>
<%
        String[] inputNames = form.getFileInputNames();
        String[] inputStatus = form.getFileInputStatus();
        for (int i = 0; i < inputNames.length; i++)
        {
            String status = "";
            if (inputStatus != null && inputStatus[i] != null)
            {
                status = " (<b>" + h(inputStatus[i]) + "</b>)";
                hasRun = true;
            }
            %><tr><td><%=h(inputNames[i])%><%=status%>
                <input type="hidden" name="fileInputNames" value="<%=h(inputNames[i])%>"></td>
            <td>&nbsp;</td></tr><%
        } %>
        </table><%
    }
%>
        </td>
    </tr>
    <tr><td class='labkey-form-label'>Paramters:</td>
        <td>
<%  if ("".equals(form.getProtocol()))
    { %>
            <textarea style="width: 100%;" name="configureXml" cols="150" rows="20"><%=form.getConfigureXml()%></textarea><br>
<%  }
    else
    { %>
<pre>
<%=h(form.getConfigureXml())%>
</pre>
<%  }

    if ("".equals(form.getProtocol()))
    {
        %><tr><td></td><td><input type="checkbox" name="saveProtocol" <% if (form.isSaveProtocol()) { %>checked<% } %>/> Save protocol for future use</td></tr><%
    }
    if (hasWork)
    {
        if (hasRun)
        {
            %><tr><td colspan="2"><labkey:button text="Retry"/>&nbsp;<labkey:button text="Cancel" href="<%=up.urlReferer(getContainer())%>"/></td></tr><%            
        }
        else
        {
            %><tr><td colspan="2"><labkey:button text="Analyze"/>&nbsp;<labkey:button text="Cancel" href="<%=up.urlReferer(getContainer())%>"/></td></tr><%
        }
    }
    else
    {
        %><tr><td colspan="2"><labkey:button text="Cancel" href="<%=up.urlReferer(getContainer())%>"/></td></tr><%        
    }
%>
</table>
</form>
<script>
    function changeProtocol(sel)
    {
        document.getElementsByName("runAnalysis")[0].value = false;
        document.getElementById("analysis_form").submit();
    }
</script>
<script for="window" event="onload">
    try {document.getElementByName("protocol").focus();} catch(x){}
</script>
