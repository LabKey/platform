<%
/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.remoteapi.RemoteConnections" %>
<%
    Container c = getContainer();
    RemoteConnections.RemoteConnectionForm remoteConnectionForm = ((JspView<RemoteConnections.RemoteConnectionForm>) HttpView.currentView()).getModelBean();
    String name = remoteConnectionForm.getConnectionName();
%>
<p>
    Please confirm that you would like to delete this connection.
</p>
<labkey:form name="editConnection" action="<%=QueryController.RemoteQueryConnectionUrls.urlDeleteRemoteConnection(c, null) %>" method="post">
<table>
    <tr>
        <td>Connection Name: </td>
        <td><input type="text" name="connectionName" size="50" value="<%=h(name)%>" readonly><br></td>
    </tr>
</table>
    <%= button("delete").submit(true) %>
    <%= button("cancel").href(QueryController.ManageRemoteConnectionsAction.class, getContainer()) %>
</labkey:form>

