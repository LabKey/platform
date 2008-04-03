<%@ page import="org.labkey.api.pipeline.PipelineUrls"%>
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.ThemeFont"%>
<%@ page import="org.labkey.pipeline.analysis.AnalysisController" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.pipeline.analysis.AnalyzePage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AnalysisController.AnalyzeForm form = getForm();
    PipelineUrls up = urlProvider(PipelineUrls.class);

    boolean hasWork = false;
%>

<labkey:errors />

<span style="font-size:<%=ThemeFont.getThemeFont().getHeader_1Size()%>">A file analysis protocol is defined by the set of parameters used to run the commands in the pipeline.</span>
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
<table border="0">
    <tr><td class='ms-searchform'>Analysis Protocol:</td>
        <td class='ms-vb'><select name="protocol"
                            onchange="changeProtocol(this)">
            <option value="">&lt;New Protocol&gt;</option>
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
    <tr><td class='ms-searchform'>Protocol Name:</td>
        <td class='ms-vb'><input type="text" name="protocolName" size="40" value="<%=h(form.getProtocolName())%>"></td></tr>
    <tr><td class='ms-searchform'>Protocol Description:</td>
        <td class='ms-vb'><textarea style="width: 100%;" name="protocolDescription" cols="150" rows="4"><%=h(form.getProtocolDescription())%></textarea></td></tr>
<%  } %>

<%  if (form.getFileInputNames().length != 1)
    { %>
    <tr><td class='ms-searchform'>Analyze Files:</td>
<%  }
    else
    { %>
    <tr><td class='ms-searchform'>Analyze File:</td>
<%  } %>
        <td class='ms-vb'>
<%
    if (form.getFileInputNames().length == 0)
        out.print("No files found");
    else
    {
        hasWork = true; %>
        <table border="0" cellpadding="0" cellspacing="3">
<%
        for (String fileName : form.getFileInputNames())
        {
            %><tr><td class='ms-vb'><%=h(fileName)%>
                <input type="hidden" name="fileInputNames" value="<%=h(fileName)%>"></td>
            <td class='ms-vb'>&nbsp;</td></tr><%
        } %>
        </table><%
    }
%>
        </td>
    </tr>
    <tr><td class='ms-searchform'>Paramters:</td>
        <td class='ms-vb'>
<%  if ("".equals(form.getProtocol()))
    { %>
            <textarea style="width: 100%" name="configureXml" cols="150" rows="20"><%=form.getConfigureXml()%></textarea><br>
<%  }
    else
    { %>
<pre>
<%=h(form.getConfigureXml())%>
</pre>
<%  }

    if ("".equals(form.getProtocol()))
    {
        %><tr><td></td><td class='ms-vb'><input type="checkbox" name="saveProtocol" <% if (form.isSaveProtocol()) { %>checked<% } %>/> Save protocol for future use</td></tr><%
    }
    if (hasWork)
    {
        %><tr><td colspan="2"><labkey:button text="Analyze"/>&nbsp;<labkey:button text="Cancel" href="<%=up.urlReferer(getContainer())%>"/></td></tr><%
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
