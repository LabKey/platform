<%
/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.DeleteFolderAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ManageFoldersForm> me = (JspView<ManageFoldersForm>) HttpView.currentView();
    ManageFoldersForm form = me.getModelBean();
    List<Container> containersToDelete = form.getTargetContainers(getContainer());
    Container primaryContainer = getContainer();
    User user = getUser();

    String message = containersToDelete.size() == 1 ? containersToDelete.get(0).isProject() ? "project and its subfolders" : "folder" : "folders";

    %>
        <table class="labkey-data-region">
            <tr><td>You are about to delete the following <%=h(message)%>:</td></tr>
        </table>
        <p/>
    <%
    for (Container c : containersToDelete)
    {
        String containerType = c.getContainerType().getContainerNoun(c);
        String childrenDescription = (c.isProject() ? "folder" : "subfolder");

        %>
        <table class="labkey-data-region">
        <%

        if (c.equals(ContainerManager.getHomeContainer()))
        {
            %>
            <tr><td>You cannot delete the home project.</td></tr>
            <tr><td>
                <%= button("OK").href(urlProvider(AdminUrls.class).getManageFoldersURL(c)) %>
            </td></tr>
            </table>
            <%

            return;
        }

        if (c.equals(ContainerManager.getSharedContainer()))
        {
            %>
            <tr><td>You cannot delete the Shared project.</td></tr>
            <tr><td>
                <%= button("OK").href(urlProvider(AdminUrls.class).getManageFoldersURL(c)) %>
            </td></tr>
            </table>
            <%

            return;
        }

        // A user must have admin permissions to the entire tree.
        if (c.hasChildren() && !ContainerManager.hasTreePermission(c, user, AdminPermission.class))
        {
            %>
            <tr><td>This <%=h(containerType)%> has <%=h(childrenDescription)%>s, but you don't have administrative permissions to all the <%=h(childrenDescription)%>s.</td></tr>
            <tr><td>&nbsp;</td></tr>
            <tr><td>
                <%= button("Back").href(urlProvider(AdminUrls.class).getManageFoldersURL(primaryContainer)) %>
            </td></tr>
            </table>
            <%

            return;
        }

        // Simplify the confirmation message in this case
        boolean singleEmptyContainer = !c.hasChildren() && ModuleLoader.getInstance().getModuleSummaries(c).isEmpty();
        if (!singleEmptyContainer)
        {
            Set<Container> containers = ContainerManager.getAllChildren(c);
            %>
            <td><ul><%

            for (Container container : containers)
            {
                Collection<String> messages = ModuleLoader.getInstance().getModuleSummaries(container);

                %><li><%=h(container.getPath().substring(1))%><%

                if (null != messages && messages.size() > 0)
                {
                    %>, containing the following objects:<ul class=star><%

                    for (String m : messages)
                    {
                        %><li><%=h(m)%></li><%
                    } %>
                    </ul><%
                } %>
                </li>
                <%
            } %>
            </ul></td></tr><%
        }
        else
        {
            %>
            <tr><td><ul><li><%=h(c.getPath().substring(1))%><%=h(c.canHaveChildren() ? ", all its subfolders," : "")%> and all the objects it contains</li></ul></td></tr>
            <%
        }
            %>
        </table>

        <%

    }

        String containerType = containersToDelete.size() == 1 ? containersToDelete.get(0).getContainerNoun() : "";
        String name = containersToDelete.size() == 1 ? containersToDelete.get(0).getName() : "these " + containersToDelete.size() +" folders";
        boolean showSubfolder = containersToDelete.size() != 1;
        boolean hasSubFolders = containersToDelete.size() == 1 && containersToDelete.get(0).getChildren().size() != 0;
        boolean usePlural = containersToDelete.size() > 1 || hasSubFolders;;

        %>
        <%=h(usePlural ? "They" : "It")%> may contain other objects that are not listed.
        <br><br>
        <table>
            <tr><td>Are you <u>sure</u> you want to permanently delete <%=h(containerType)%> <b><%=h(name)%></b><%=h(showSubfolder || hasSubFolders ? ", all its subfolders," : "")%> and all the objects <%=h(usePlural ? "they contain" : "it contains")%>?</td></tr>
            <tr><td>&nbsp;</td></tr>
        </table>

        <labkey:form action='<%=urlFor(DeleteFolderAction.class)%>' method="post">
            <% if (form.getReturnUrl() != null) { %>
            <%= generateReturnUrlFormField(form)%>
            <% } %>
            <%
            for (Container c : containersToDelete)
            {
                %>
                    <input type="hidden" name="targets" value="<%=h(c.getId())%>"/>
                <%
            }
            %>
            <%= button("Yes, delete all").disableOnClick(true).submit(true) %>
            <%= button("Cancel").href(urlProvider(AdminUrls.class).getManageFoldersURL(getContainer())) %>
        </labkey:form>
