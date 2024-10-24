<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    boolean isTroubleshooter = c.isRoot() && !c.hasPermission(getUser(), AdminOperationsPermission.class);
    AdminController.ExternalHostsForm bean = (AdminController.ExternalHostsForm) HttpView.currentModel();
%>
<labkey:errors/>
<%=bean.getTypeEnum().getDescription()%>

<%
    if (isTroubleshooter)
    {
%>
<%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>
<%
    }
    else
    {
%>
<labkey:form method="post">
    <table>
        <tr>
            <td class="labkey-form-label"><label for="newExternalHostTextField">Host</label></td>
            <td><input name="newExternalHost" id="newExternalHostTextField" size="75" /></td>
        </tr>
        <tr>
            <td><br/><input type="hidden" id="saveNew" name="saveNew" value="true"><%= button("Save").submit(true) %></td>
        </tr>
    </table>
</labkey:form>
<%
    }
%>
