<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.reports.report.DefaultScriptRunner" %>
<%@ page import="org.labkey.api.reports.report.RServeScriptRunner" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<ReportsController.ConfigureRForm> me = (JspView<ReportsController.ConfigureRForm>) HttpView.currentView();
    ReportsController.ConfigureRForm bean = me.getModelBean();

    String options =
        "<option value=" + org.labkey.api.security.SecurityManager.PermissionSet.ADMIN.getPermissions() + ">Admin</option>" +
        "<option value=" + SecurityManager.PermissionSet.EDITOR.getPermissions() + ">" + SecurityManager.PermissionSet.EDITOR.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.AUTHOR.getPermissions() + ">" + SecurityManager.PermissionSet.AUTHOR.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.READER.getPermissions() + ">" + SecurityManager.PermissionSet.READER.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.RESTRICTED_READER.getPermissions() + ">" + SecurityManager.PermissionSet.RESTRICTED_READER.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.SUBMITTER.getPermissions() + ">" + SecurityManager.PermissionSet.SUBMITTER.getLabel() + "</option>" +
        "<option value=" + SecurityManager.PermissionSet.NO_PERMISSIONS.getPermissions() + ">" + SecurityManager.PermissionSet.NO_PERMISSIONS.getLabel() + "</option>";

%>

<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">

    function onTempFolder()
    {
        var system = YAHOO.util.Dom.get('tempFolderSystem');
        var folderLocation = YAHOO.util.Dom.get('tempFolder');

        if (system.checked)
            folderLocation.style.display = "none";
        else
            folderLocation.style.display = "";

        var permissions = YAHOO.util.Dom.get('permissions');
        permissions.value = <%=bean.getPermissions()%>;
    }

    function validateForm()
    {
        var system = YAHOO.util.Dom.get('tempFolderSystem');
        var folderLocation = YAHOO.util.Dom.get('tempFolder');

        if (system.checked)
            folderLocation.value = "";
    }

    YAHOO.util.Event.addListener(window, "load", onTempFolder)
</script>

<table border=0 cellspacing=2 cellpadding=0>
<%
    for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
    {
        %><tr><td colspan=3><font color="red" class="error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<form action="" method="post" onsubmit="validateForm();">
    <table>
        <tr class="wpHeader"><th colspan=2 align=center>R View Configuration</th></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td class=normal><i>Specify the absolute path of the R program (R.exe on Windows, R for Unix and Mac) :</i><br/></td></tr>

        <tr><td>R&nbsp;program:</td><td class="normal"><input name="programPath" style="width:400px" value="<%=StringUtils.trimToEmpty(bean.getProgramPath())%>"></td><td></td></tr>
        <tr><td>R&nbsp;command:</td><td class="normal"><input name="command" style="width:400px" value="<%=StringUtils.trimToEmpty(h(bean.getCommand()))%>"></td><td></td></tr>

        <tr><td></td><td class=normal><i>Scripts can be executed by running R in batch mode or by using an RServe server:</i><br/></td></tr>
        <tr><td>Script&nbsp;execution:</td><td class="normal"><input name="scriptHandler" value="<%=DefaultScriptRunner.ID%>" type="radio" <%=DefaultScriptRunner.ID.equals(bean.getScriptHandler()) ? "checked" : ""%>>
            Batch mode.<%=PageFlowUtil.helpPopup("Batch mode", "A new instance of R is started up in batch mode each " +
                "time a script is executed. Because the instance of R is run using the same privileges as the LabKey server, " +
                "care must be taken to ensure that security settings below are set accordingly.")%></td><td></td></tr>
        <tr><td></td><td class="normal"><input name="scriptHandler" value="<%=RServeScriptRunner.ID%>" type="radio" <%=RServeScriptRunner.ID.equals(bean.getScriptHandler()) ? "checked" : ""%>>
            RServe server.<img src="<%=HttpView.currentContext().getContextPath() + "/_images/beta.gif"%>"><%=PageFlowUtil.helpPopup("RServe server (Beta)", "RServe is a TCP/IP based server that can interact with R. " +
                "It can improve execution performance because the server does not need to be started for every script " +
                "that is run. Additionally, it can be configured on Unix systems to run under a specified group or user ID. RServe " +
                "is a separate R library that must be installed by your R administrator.")%></td><td></td></tr>
        <tr><td>&nbsp;</td></tr>

        <tr><td></td><td class=normal><i>Specify the permissions required in order to create R Views:</i><br/></td></tr>
        <tr><td>Permissions:</td><td class="normal">
            <select name="permissions" id="permissions"><%=options%></select></td><td></td></tr>
        <tr>
            <td>Temp&nbsp;directory:<%=PageFlowUtil.helpPopup("Temporary Folder", "In order to execute R scripts on the LabKey server, temporary files need to be created. The folder location specified " +
                "must be accesible by the LabKey server. Alternatively, the system temporary location will be used.")%>
            </td>
            <td class="normal"><input name="tempFolderRadio" value="folder" type="radio" onclick="onTempFolder();" <%=StringUtils.isEmpty(bean.getTempFolder()) ? "" : "checked"%>>Specify a folder location&nbsp;&nbsp;<input name="tempFolder" id="tempFolder" style="width:200px;display:none" value="<%=StringUtils.trimToEmpty(bean.getTempFolder())%>"></td>
            <td></td>
        </tr>
        <tr><td></td><td class="normal"><input name="tempFolderRadio" value="system" id="tempFolderSystem" type="radio" onclick="onTempFolder();" <%=StringUtils.isEmpty(bean.getTempFolder()) ? "checked" : ""%>>Use the system temporary folder</td><td></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>&nbsp;</td>
            <td><input type="image" src="<%=PageFlowUtil.submitSrc()%>">
            &nbsp;<%=PageFlowUtil.buttonLink("Done", urlProvider(AdminUrls.class).getAdminConsoleURL())%></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><i>The configuration of this page is necessary to be able to create an R view. The location
            of the R program must be accessible by the LabKey server. The R command is the command used by the LabKey server
            to execute scripts created in an R view. The default command is sufficient for most cases and usually
            would not need to be modified.</i><br/><br/></td>
        </tr>
        <tr><td></td><td><i>Application downloads, documentation and tutorials about the R language can be found at
            the <a target="_blank" href="http://www.r-project.org/">R Project website</a>.</i>
        </tr>
    </table>
</form>

