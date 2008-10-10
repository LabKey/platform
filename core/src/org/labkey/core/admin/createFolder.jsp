<%
/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    String name = form.getName();
    String containerDescription = (c.isRoot() ? "Project" : "Folder");
    String containerType = containerDescription.toLowerCase();
%>

<form name="createForm" action="createFolder.view" method="post">
    <%=formatMissedErrors("form")%>
    <table>
        <tr><td colspan=2><%
        if (!c.isRoot())
        {
            %>New <%=h(containerType)%> under <b><%=h(c.getName())%></b><%
        }
        else
        {
            %>New <%=h(containerType)%><%
        } %>
        </tr>
        <tr>
            <td class="labkey-form-label">Name:</td>
            <td><input id="name" name="name" value="<%=h(name)%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label">
                Folder Type:
            </td>
            <td>
                <table>
<%
    String folderTypeName = form.getFolderType();
    if (null == folderTypeName) //Try to avoid "None"
        folderTypeName = FolderType.NONE.equals(c.getFolderType()) ? "Collaboration" : c.getFolderType().getName();
    int radioIndex = 0;
    for (FolderType ft : ModuleLoader.getInstance().getFolderTypes())
    {
%>
                    <tr>
                        <td valign="top">
                            <input type="radio" name="folderType" value="<%=h(ft.getName())%>" <%=folderTypeName.equals(ft.getName()) ? "checked" : ""%> >
                         </td>
                        <td valign="top">
                            <span style="cursor:pointer;font-weight:bold" onclick="document.createForm.folderType[<%=radioIndex%>].checked = true;"><%=h(ft.getLabel())%></span><br>
                            <%=h(ft.getDescription())%>
                        </td>
                    </tr>
<%
    radioIndex++;
}
%>
                </table>
            </td>
        </tr>
    </table>

    <table class="labkey-button-bar">
        <tr>
            <td><%=PageFlowUtil.generateSubmitButton("Next")%></td>
            <td><%
                if (!c.isRoot())
                {
                    %><%=PageFlowUtil.generateButton("Cancel", "manageFolders.view")%><%
                }
                else
                {
                    %><%=PageFlowUtil.generateButton("Cancel", "createFolder.view", "window.history.back(); return false;")%><%
                } %></td>
        </tr>
    </table>
</form>
