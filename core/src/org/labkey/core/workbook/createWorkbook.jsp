<%
/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.FolderTypeManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.workbook.CreateWorkbookBean" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CreateWorkbookBean> me = (JspView<CreateWorkbookBean>) HttpView.currentView();
    CreateWorkbookBean bean = me.getModelBean();
    Container container = getContainer();

    Set<FolderType> folderTypes = new LinkedHashSet<>();
    for (FolderType folderType : FolderTypeManager.get().getEnabledFolderTypes())
    {
        if (folderType.isWorkbookType())
        {
            folderTypes.add(folderType);
        }
    }
%>
<style type="text/css">
    .cwb-layout-table
    {
        width: 100%;
        padding: 2px;
    }
    .cwb-input
    {
        width: 100%
    }
    .cwb-button-bar
    {
        text-align:right;
    }
    td.labkey-form-label
    {
        width: 1%;
    }
</style>
<labkey:errors/>
<script type="text/javascript">
    function success(workbookInfo, request)
    {
        window.location = LABKEY.ActionURL.buildURL("project", "start", workbookInfo.path);
    }

    function submitForm(form)
    {
        var config =
        {
            isWorkbook: true,
            scope: this,
            successCallback: success,
            errorCallback: function(error){
                if(error.exception)
                    alert(error.exception);
                else
                    alert('There was an error creating the workbook');

                //Issue 14279: Create Workbook button doesn't reenable after failed insert attempt
                var id = Ext.get(form).child('input[type="submit"]').id;
                var buttons = Ext.get(form).query('a');
                Ext.each(buttons, function(btn){
                    Ext.get(btn).replaceClass('labkey-disabled-button', 'labkey-button');
                }, this);
            },
            description: document.getElementById("workbookDescription").value,
            title: document.getElementById("workbookTitle").value
        };
        if (document.getElementById("workbookFolderType") &&
                document.getElementById("workbookFolderType").value &&
                document.getElementById("workbookFolderType").value != "")
        {
            config.folderType = document.getElementById("workbookFolderType").value;
        }

        LABKEY.Security.createContainer(config);
        return false;
    }
</script>
<labkey:form onsubmit="submitForm()">
    <table class="cwb-layout-table">
        <tr>
            <td class="labkey-form-label">Title:</td>
            <td><input id="workbookTitle" type="text" name="title" class="cwb-input" value="<%=h(bean.getTitle())%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description:</td>
            <td>
                <textarea id="workbookDescription" name="description" rows="4" cols="40" class="cwb-input"><%=null == bean.getDescription() ? "" : h(bean.getDescription())%></textarea>
            </td>
        </tr>
        <% if (folderTypes.size() > 1) { %>
            <tr>
                <td class="labkey-form-label">Type:</td>
                <td>
                    <select id="workbookFolderType" name="folderType">
                        <option value="" />
                        <% for (FolderType folderType : folderTypes) { %>
                            <option value="<%=h(folderType.getName()) %>" <% if (folderType.getName().equals(bean.getFolderType())) { %>selected="true" <% } %>><%=h(folderType.getLabel()) %></option>
                        <% } %>
                    </select>
                </td>
            </tr>
        <% } %>
        <tr>
            <td colspan="2" class="cwb-button-bar">
                <%= button("Cancel").href(container.getStartURL(getUser())) %>
                <%= button("Create Workbook").submit(true).disableOnClick(true) %>
            </td>
        </tr>
    </table>
</labkey:form>
