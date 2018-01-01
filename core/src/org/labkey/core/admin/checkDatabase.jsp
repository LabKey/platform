<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.DataCheckForm> me = (JspView<AdminController.DataCheckForm>) HttpView.currentView();
    AdminController.DataCheckForm bean = me.getModelBean();
%>

<labkey:errors/>

<labkey:form action="<%=h(buildURL(AdminController.GetSchemaXmlDocAction.class))%>" method="get">
    <table>
        <tr><td>Check table consistency:&nbsp;</td>
        <td> <%= button("Do Database Check").href(new ActionURL(AdminController.DoCheckAction.class, ContainerManager.getRoot())) %>&nbsp;</td></tr>
        <tr><td>&nbsp;</td><td></td></tr>
        <tr><td>Validate domains match hard tables:&nbsp;<br/>
        (Runs in background as pipeline job)</td>
        <td> <%= button("Validate").href(new ActionURL(AdminController.ValidateDomainsAction.class, ContainerManager.getRoot())) %>&nbsp;</td></tr>
        <tr><td>&nbsp;</td><td></td></tr>
        <tr><td>Get schema xml doc:&nbsp;</td>
            <td>
                <select id="dbSchema" name="dbSchema" style="width:250px"><%
                    for (Module m : bean.getModules())
                    {
                        for (String sn : m.getSchemaNames())
                        {
                        %>
                        <option value="<%=h(sn)%>"><%=h(m.getName() + " : " + sn)%></option >
                        <%
                        }
                   }
                    %>
                </select><br>
            </td></tr>
        <tr><td></td><td><%= button("Get Schema Xml").submit(true) %></td></tr>
    </table>
    <br/>
    <%= button("Cancel").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
</labkey:form><br/><br/>

