<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.module.FolderType"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<labkey:errors/>

<%
    AdminController.FolderTypesBean bean = (AdminController.FolderTypesBean) HttpView.currentModel();
    boolean hasAdminPerm = getContainer().hasPermission(getUser(), AdminPermission.class);
%>
<div>
    If a folder type is disabled, it will not be available as a choice when setting the type for a folder or project.
    Any folders that are already using it will be unaffected.
</div>
<br/>
<labkey:form method="post">
    <table class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <td class="labkey-column-header">Enabled</td>
            <td class="labkey-column-header">Name</td>
            <td class="labkey-column-header">Description</td>
        </tr>
        <%
            int rowCount = 0;
            for (FolderType folderType : bean.getAllFolderTypes())
            {
        %>
            <tr class="<%=h(rowCount % 2 == 0 ? "labkey-alternate-row" : "labkey-row")%>">
                <td><input type="checkbox" name="<%= h(folderType.getName())%>"<%=checked(bean.getEnabledFolderTypes().contains(folderType))%> value="true" /></td>
                <td><%= h(folderType.getName()) %></td>
                <td><%= h(folderType.getDescription()) %></td>
            </tr>
        <%
                rowCount++;
            }
        %>
    </table>
    <br/>
    <%= hasAdminPerm ? button("Save").submit(true) : "" %>
    <%= PageFlowUtil.generateBackButton(!hasAdminPerm ? "Done" : "Cancel") %>
</labkey:form>
