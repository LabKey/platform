<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.NetworkDriveProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.ShowNetworkDriveTestAction" %>
<%@ page import="org.labkey.core.admin.AdminController.SiteSettingsBean" %>
<%@ page import="org.labkey.core.admin.FilesSiteSettingsAction" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    SiteSettingsBean bean = ((JspView<SiteSettingsBean>) HttpView.currentView()).getModelBean();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    var testNetworkDrive;
    (function(){

        testNetworkDrive = function()
        {
            var preferenceForm = document.forms['networkDrive'];
            var networkDriveForm = document.forms['networkdrivetest'];
            if (preferenceForm.networkDriveLetter.value.length == 0){
                alert("Please specify your drive letter before testing.");
                try {preferenceForm.networkDriveLetter.focus();} catch(x){}
                return;
            }
            if (preferenceForm.networkDrivePath.value.length == 0){
                alert("Please specify your drive path before testing.");
                try {preferenceForm.networkDrivePath.focus();} catch(x){}
                return;
            }
            if (preferenceForm.networkDriveUser.value.length == 0){
                alert("Please specify a user before testing.");
                try {preferenceForm.networkDriveUser.focus();} catch(x){}
                return;
            }
            if (preferenceForm.networkDrivePassword.value.length == 0)
            {
                alert("Please specify a password before testing.");
                try {preferenceForm.networkDrivePassword.focus();} catch(x){}
                return;
            }
            networkDriveForm.networkDriveLetter.value = preferenceForm.networkDriveLetter.value;
            networkDriveForm.networkDrivePath.value = preferenceForm.networkDrivePath.value;
            networkDriveForm.networkDriveUser.value = preferenceForm.networkDriveUser.value;
            networkDriveForm.networkDrivePassword.value = preferenceForm.networkDrivePassword.value;

            networkDriveForm.submit();
        };
    })();
</script>

<p>
    LabKey Server runs on a Windows server as an operating system service, which Windows treats as a separate user account.
    The user account that represents the service may not automatically have permissions to access a network share that the
    logged-in user does have access to. If you are running on Windows and using LabKey Server to access files on a remote server,
    for example via the LabKey Server pipeline, you'll need to configure the server to map the network drive for the service's user account.<br>
    (<%=bean.helpLink%>)
</p>

<labkey:errors/>
<labkey:form name="networkDrive" method="post" layout="horizontal">

    <labkey:input type="text" label="Drive letter *" name="networkDriveLetter" id="networkDriveLetter" value="<%= NetworkDriveProps.getNetworkDriveLetter() %>" size="1" maxLength="1" isRequired="true"/>
    <labkey:input type="text" label="Path *" name="networkDrivePath" id="networkDrivePath" value="<%= NetworkDriveProps.getNetworkDrivePath() %>" size="50" isRequired="true"/>
    <labkey:input type="text" label="User *" name="networkDriveUser" id="networkDriveUser" value="<%= NetworkDriveProps.getNetworkDriveUser() %>"
                  isRequired="true" contextContent="Provide a valid user name for logging onto the share; you can specify the value 'none' if no user name or password is required" forceSmallContext="true"/>
    <labkey:input type="password" label="Password *" name="networkDrivePassword" id="networkDrivePassword"
                  isRequired="true" contextContent="Provide the password for the user name; you can specify the value 'none' if no user name or password is required" forceSmallContext="true"/>

    <labkey:button text="save" submit="true"/>
    <labkey:button text="cancel" href="<%=new ActionURL(FilesSiteSettingsAction.class, getContainer())%>"/>
    <% if (hasAdminOpsPerms) { %>
    <labkey:button text="test settings" submit="false" onclick="testNetworkDrive();"/>
    <% } %>
</labkey:form>

<labkey:form name="networkdrivetest" action="<%=urlFor(ShowNetworkDriveTestAction.class)%>" enctype="multipart/form-data" method="post" target="_new">
    <input type="hidden" name="networkDriveLetter" value="" />
    <input type="hidden" name="networkDrivePath" value="" />
    <input type="hidden" name="networkDriveUser" value="" />
    <input type="hidden" name="networkDrivePassword" value="" />
</labkey:form>
