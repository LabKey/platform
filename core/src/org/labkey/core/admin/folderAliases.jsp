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
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String[] pathAliases = ContainerManager.getAliasesForContainer(getContainer());
%>
<table width="500">
    <tr>
        <td>Folder aliases allow you to redirect other URLs on this server to this folder.</td>
    </tr>
    <tr>
        <td>
            For example, if you enter <b>/otherproject/otherfolder</b> below,
            URLs directed to a folder with name <b>/otherproject/otherfolder</b>
            will be redirected to this folder, <b><%= getContainer().getPath() %></b>.
        </td>
    </tr>
    <tr>
        <td>Enter one alias per line. Each alias should start with a '/'. Aliases that are
            paths to real folders in the system will be ignored.</td>
    </tr>
    <tr>
        <td>
        <labkey:form action="<%=h(buildURL(AdminController.FolderAliasesAction.class))%>" method="post">
            <textarea rows="4" cols="40" name="aliases"><%
                StringBuilder sb = new StringBuilder();
                String separator = "";
                for (String path : pathAliases)
                {
                    sb.append(separator);
                    separator = "\r\n";
                    sb.append(path);
                }%><%= sb.toString() %></textarea><br><br>
            <%= button("Save Aliases").submit(true) %>
            <%= button("Cancel").href(urlProvider(AdminUrls.class).getManageFoldersURL(getContainer())) %>
        </labkey:form>
        </td>
    </tr>
</table>
