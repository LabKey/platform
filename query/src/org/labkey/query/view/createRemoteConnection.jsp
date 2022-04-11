<%
/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.remoteapi.RemoteConnections" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();

    RemoteConnections.RemoteConnectionForm remoteConnectionForm = ((JspView<RemoteConnections.RemoteConnectionForm>) HttpView.currentView()).getModelBean();
    String name = remoteConnectionForm.getConnectionName();
    String url = remoteConnectionForm.getUrl();
    String userEmail = remoteConnectionForm.getUserEmail();
    String folderPath = remoteConnectionForm.getFolderPath();
    String connectionKind = remoteConnectionForm.getConnectionKind();
    boolean editConnection = StringUtils.isNotEmpty(name);
    String nameToShow = editConnection ? name : remoteConnectionForm.getNewConnectionName();
%>
<p><%=h(RemoteConnections.MANAGEMENT_PAGE_INSTRUCTIONS)%></p>
<labkey:errors/>
<br>
<labkey:form name="editConnection" action="<%=QueryController.RemoteQueryConnectionUrls.urlSaveRemoteConnection(c) %>" method="post" layout="horizontal">
    <labkey:input type="text" label="Connection Name *" name="newConnectionName" id="newConnectionName" size="50" value="<%=nameToShow%>" isRequired="true"/>
    <labkey:input type="text" label="Server URL *" name="url" id="url" size="50" value="<%=url%>" forceSmallContext="true"
                  contextContent="Enter in the server URL. Include both the protocol (http:// or https://) and a context path if necessary. As an example, http://localhost:8080/labkey would be a valid name."
                  isRequired="true"/>
    <labkey:input type="text" label="User *" name="userEmail" id="userEmail" size="50" value="<%=userEmail%>" isRequired="true"/>
    <labkey:input type="password" label="Password *" name="password" id="password" size="50" isRequired="true"/>
    <labkey:input type="text" label="Folder Path *" name="folderPath" id="folderPath" size="50" value="<%=folderPath%>"
                  contextContent="Enter the folder path on the LabKey server. An example folder path is 'My Folder/My Subfolder'." forceSmallContext="true"
                  isRequired="true"/>

    <labkey:input type="hidden" name="connectionName"  value="<%=name%>"/>
    <labkey:input type="hidden" name="connectionKind"  value="<%=connectionKind%>"/>

    <labkey:button text="save" submit="true"/>
    <labkey:button text="cancel" href="<%=new ActionURL(QueryController.ManageRemoteConnectionsAction.class, getContainer())%>"/>
</labkey:form>