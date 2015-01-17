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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p>
    Administrators can define external remote connections to alternate LabKey servers.
    This feature should be used with care since, depending
    on your configuration, any user with access to the remote site could view arbitrary data in your remote server.
</p>
<%
    Container c = getContainer();
%>

<br>
<%
    Map<String, String> connectionMap = ((JspView<Map<String,String>>) HttpView.currentView()).getModelBean();
    if (connectionMap == null)
    { %>
        <p style="color: red">MasterEncryptionKey has not been specified in labkey.xml.</p>
<%  }
    else
    {
        for (String field : connectionMap.keySet())
        {
            %> <labkey:link href="<%= QueryController.RemoteQueryConnectionUrls.urlEditRemoteConnection(c, connectionMap.get(field))%>" text="edit"/> <%
            %> <labkey:link href="<%= QueryController.RemoteQueryConnectionUrls.urlDeleteRemoteConnection(c, connectionMap.get(field))%>" text="delete"/> <%
            %> <labkey:link href="<%= QueryController.RemoteQueryConnectionUrls.urlTestRemoteConnection(c, connectionMap.get(field))%>" text="test"/> <%
            %> <%= h(connectionMap.get(field)) %> <br/> <%
        }
%> <p/>
<labkey:link href="<%= QueryController.RemoteQueryConnectionUrls.urlCreateRemoteConnection(c) %>" text="create new connection"/> <%
    }
%>
