<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.controllers.QueryController.RemoteQueryConnectionUrls" %>
<%@ page import="org.labkey.remoteapi.RemoteConnections" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p><%=h(RemoteConnections.MANAGEMENT_PAGE_INSTRUCTIONS)%></p>
<%
    Container c = getContainer();
    boolean hasAdminOpsPerm = c.hasPermission(getUser(), AdminOperationsPermission.class);
%>

<br>
<%
    Map<String, String> connectionMap = ((JspView<Map<String,String>>) HttpView.currentView()).getModelBean();
    if (connectionMap == null)
    { %>
        <p style="color: red">EncryptionKey has not been specified in <%= h(AppProps.getInstance().getWebappConfigurationFilename()) %>, or its value no longer matches key previously in use.</p>
        <labkey:form method="post">
            <input type="hidden" name="reset" value="true" />
            <labkey:button text="Reset Remote Configurations"></labkey:button>
        </labkey:form>
<%  }
    else
    {
        for (String field : connectionMap.keySet())
        {
            %><%=link("edit").href(RemoteQueryConnectionUrls.urlEditRemoteConnection(c, connectionMap.get(field)))%><%
            %><%=link("delete").href(RemoteQueryConnectionUrls.urlDeleteRemoteConnection(c, connectionMap.get(field)))%><%
            %><%=link("test").href(RemoteQueryConnectionUrls.urlTestRemoteConnection(c, connectionMap.get(field)))%><%
            %><%=h(connectionMap.get(field))%>
            <br/><%
        }
%> <p/>
<%
        if (hasAdminOpsPerm)
        {
            %><%=link("create new connection").href(RemoteQueryConnectionUrls.urlCreateRemoteConnection(c))%><%
        }
    }
%>
