<%
    /*
     * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    Container c = getContainer();

    String name = form.getName();
    String title = form.getTitle();

    String disabled = c.isTitleFieldSet() ? "" : "disabled";
    String checked = !c.isTitleFieldSet() ? "checked" : "unchecked";

    if (null == name)
    {
        name = c.getName();
    }

    if (null == title)
    {
        title = c.getTitle();
    }


    String containerType = (c.isProject() ? "Project" : "Folder");

    // 16221
    if (ContainerManager.isRenameable(c))
    {
%>
<div>
    <style type="text/css">
        .name-setings-form {
            list-style: none;
            padding: 0;
            width: 320px;
        }

        .name-setings-form li {
            margin-bottom: 8px;
        }

        .name-setings-form input[type="text"] {
            width: 100%;
        }

        .name-setings-form input[type="checkbox"] {
            vertical-align: bottom;
        }

        .name-setings-form label {
            display: block;
            vertical-align: top;
            margin-bottom: 4px;
            font-weight: bold;
        }

        .change-title-cb {
            margin-left: 20px;
            font-weight: normal;
        }

        .change-title-cb input {
            vertical-align: bottom;
        }
    </style>
    <labkey:form action="<%=h(buildURL(AdminController.RenameFolderAction.class))%>" method="post">
        <%=formatMissedErrors("form")%>
        <ul class="name-setings-form">
            <li>
                <label for="name"><%= text(containerType)%> Name</label>
                <input type="text" id="name" name="name" value="<%=h(name)%>">
            </li>
            <li>
                <label for="title">
                    <%= text(containerType)%> Title
                    <span class="change-title-cb">
                        <input type="checkbox" id="titleSameAsName" name="titleSameAsName" <%=h(checked)%>>same as name
                    </span>
                </label>
                <input type="text" id="title" name="title" <%=h(disabled)%> value="<%=h(title)%>">
            </li>
            <li>
                <label for="addAlias">Options</label>
                <input type="checkbox" id="addAlias" name="addAlias" checked>Alias current name (recommended)
                <%= helpPopup("Aliasing", "Add a folder alias for the folder's current name. This will make links that still target the old folder name continue to work.")%>
            </li>
            <li>
                <%= button("Save").submit(true) %>
                <%= button("Cancel").href(urlProvider(AdminUrls.class).getManageFoldersURL(c)) %>
            </li>
        </ul>
        <script>
            (function($) {

                var titleSameAsName = $('#titleSameAsName'),
                    title = $('#title'),
                    name = $('#name');

                // 'same as name' checkbox
                titleSameAsName.on('change', function() {
                    title.prop('disabled', this.checked);
                    title.val(name.val());
                });

                // title update
                name.on('change, keydown, keyup', function() {
                    if (title.prop('disabled')) {
                        title.val(name.val());
                    }
                });
            })(jQuery);
        </script>
    </labkey:form>
</div>
<%
}
else
{
%>
This folder may not be renamed as it is reserved by the system.
<%
    }
%>
