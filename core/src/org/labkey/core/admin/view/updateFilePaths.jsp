<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.apache.commons.lang3.SystemUtils" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.premium.PremiumService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController.MapNetworkDriveAction" %>
<%@ page import="org.labkey.core.admin.FileListAction" %>
<%@ page import="org.labkey.core.admin.FileSettingsForm" %>
<%@ page import="org.labkey.core.admin.FilesSiteSettingsAction" %>
<%@ page import="org.labkey.core.admin.UpdateFilePathsAction" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    UpdateFilePathsAction.UpdateFilePathsForm bean = ((JspView<UpdateFilePathsAction.UpdateFilePathsForm>)HttpView.currentView()).getModelBean();
%>

<labkey:errors/>

<labkey:form action="<%=urlFor(UpdateFilePathsAction.class)%>" method="post">

    <p style="width: 60em;">
        LabKey Server stores file paths in a variety of database tables. If files have moved from one location
        to another and the server hasn't updated its stored paths, you can use this page to make the changes.
    </p>

    <p style="width: 60em;">
        Use URIs that represent the root of the original path and the root of the new location. For example,
        <em>file:/Volumes/NetworkShare/SubDir</em> or <em>file:/labkey/build/deploy/files</em>
    </p>

    <p style="width: 60em;">
        Please use caution as this can
        break the association of metadata and download links if the updated paths don't match the actual file system
        layout.
    </p>

    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label"><label for="originalPrefix">Original path URI prefix</label></td>
            <td><input size="50" id="originalPrefix" name="originalPrefix" value="<%= h(bean.getOriginalPrefix())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="newPrefix">New path URI prefix</label></td>
            <td><input size="50" id="newPrefix" name="newPrefix" value="<%= h(bean.getNewPrefix())%>"></td>
        </tr>
        <tr>
            <td></td>
            <td><labkey:button text="Submit" /> <labkey:button text="Cancel" href="<%= PageFlowUtil.urlProvider(AdminUrls.class).getFilesSiteSettingsURL(false) %>" /></td>
        </tr>
    </table>


</labkey:form>
