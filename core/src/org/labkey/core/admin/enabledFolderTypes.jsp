<%
/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.module.FolderTypeManager" %>
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
<br/>
    The folder type selected as the default will be used as the default choice in the new project / folder creation form.
</div>
<br/>
<labkey:form method="post" onsubmit="return validate();">
    <table class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <td class="labkey-column-header">Enabled</td>
            <td class="labkey-column-header">Default</td>
            <td class="labkey-column-header">Name</td>
            <td class="labkey-column-header">Description</td>
        </tr>
        <%
            int rowCount = 0;
            for (FolderType folderType : bean.getAllFolderTypes())
            {
        %>
            <tr class="<%=h(rowCount % 2 == 0 ? "labkey-alternate-row" : "labkey-row")%>">
                <td><input type="checkbox" name="<%= h(folderType.getName())%>"<%=checked(bean.getEnabledFolderTypes().contains(folderType))%> value="true" onchange="removeAsDefaultIfDisabled(this)"/></td>
                <td><input type="radio" name="<%= h(FolderTypeManager.FOLDER_TYPE_DEFAULT)%>" value="<%= h(folderType.getName()) %>"
                        <%=checked(folderType.equals(bean.getDefaultFolderType()))%> onchange="ensureEnabledIfDefault(this)"/></td>
                <td><%= h(folderType.getName()) %></td>
                <td><%= h(folderType.getDescription()) %></td>
            </tr>
        <%
                rowCount++;
            }
        %>
    </table>
    <br/>
    <%= hasAdminPerm ? button("Save").submit(true) : HtmlString.EMPTY_STRING %>
    <%= generateBackButton(!hasAdminPerm ? "Done" : "Cancel") %>
</labkey:form>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    // If a folder type is disabled but it is currently selected as the default folder type, uncheck the radio button.
    function removeAsDefaultIfDisabled(checkbox)
    {
        if (!checkbox.checked)
        {
            // Get the radio button for the folder type that is selected as the default folder type
            var defaultFolderTypeRb = getDefaultFolderTypeRb();
            if (defaultFolderTypeRb && defaultFolderTypeRb.value === checkbox.name)
            {
               defaultFolderTypeRb.checked = false;
            }
        }
    }

    function getDefaultFolderTypeRb()
    {
        return document.querySelector('input[type=radio][name="<%=h(FolderTypeManager.FOLDER_TYPE_DEFAULT)%>"]:checked');
    }

    // If a folder type was selected as the default make sure that it is enabled
    function ensureEnabledIfDefault(radiobutton)
    {
        if (radiobutton.checked)
        {
            // Get the check box for the corresponding folder type
            var folderTypeCb = document.querySelector('input[type=checkbox][name="' + radiobutton.value + '"]');
            if (folderTypeCb && !folderTypeCb.checked)
            {
                folderTypeCb.checked = true; // enable the folder type
            }
        }
    }

    function validate()
    {
        var defaultFolderTypeRb = getDefaultFolderTypeRb()
        if(!defaultFolderTypeRb)
        {
            alert("Please select a default folder type.");
            return false;
        }
        return true;
    }
</script>
