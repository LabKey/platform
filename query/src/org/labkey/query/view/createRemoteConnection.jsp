<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<h2>Create A Remote Connection</h2>
<p>
    Administrators can define external remote connections to alternate LabKey servers.
    This feature should be used with care since, depending
    on your configuration, any user with access to the remote site could view arbitrary data in your remote server.
</p>
<%
    Container c = getContainer();
    boolean isAdmin = getUser().isSiteAdmin();
    String name = request.getParameter("connectionName");

    Map<String, String> map1 = PropertyManager.getWritableProperties(QueryController.REMOTE_CONNECTIONS_CATEGORY + ":" + name, true);
    String url = map1.get("URL");
    String user = map1.get("user");
    String password = map1.get("password");
    String container = map1.get("container");

%>

<br>

<%
    if (isAdmin)
    {
%>
<form name="editConnection" action="<%=QueryController.RemoteConnectionUrls.urlEditRemoteConnectionSubmit(c) %>" method="post">
<table>
    <tr>
        <td>Connection Name:</td>
        <td><input type="text" name="connectionName" size="50" value="<%=h(name)%>"><br></td>
    </tr>
    <tr>
        <td>Server URL:</td>
        <td><input type="text" name="url" size="50" value="<%= h(url) %>"><br></td>
    </tr>
    <tr>
        <td>User: </td>
        <td><input type="text" name="user" size="50" value="<%= h(user)%>"></td>
    </tr>
    <tr>
        <td>Password: </td>
        <td><input type="text" name="password" size="50" value="<%=h(password)%>"></td>
    </tr>
    <tr>
        <td>Container: </td>
        <td><input type="text" name="container" size="50" value="<%=h(container)%>"></td>
    </tr>
</table>
    <%= generateSubmitButton("save")%>
</form>
<%
    }
%>