<%
/*
 * Copyright (c) 2013-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.controllers.QueryController.RemoteQueryConnectionUrls" %>
<%@ page import="org.labkey.remoteapi.RemoteConnections" %>
<%@ page import="java.net.MalformedURLException" %>
<%@ page import="java.net.URL" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p><%=h(RemoteConnections.MANAGEMENT_PAGE_INSTRUCTIONS)%></p>
<%
    Container c = getContainer();
    boolean hasAdminOpsPerm = c.hasPermission(getUser(), AdminOperationsPermission.class);

    Map<String, String> connectionMap = ((JspView<Map<String,String>>) HttpView.currentView()).getModelBean();

    List<String> cleartextConnections = new ArrayList<>();
    if (connectionMap != null)
    {
        for (String connectionName : connectionMap.values())
        {
            Map<String, String> props = RemoteConnections.getRemoteConnection(
                    RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY, connectionName, c);
            String connUrl = props.get(RemoteConnections.FIELD_URL);
            if (connUrl != null)
            {
                try
                {
                    if (RemoteConnections.isCleartextHttpUrl(new URL(connUrl)))
                        cleartextConnections.add(connectionName);
                }
                catch (MalformedURLException ignored)
                {
                    // Malformed URLs are surfaced by server-side validation in createOrEditRemoteConnection; suppress here.
                }
            }
        }
    }
%>

<% if (!cleartextConnections.isEmpty()) { %>
<p class="labkey-warning-messages">
    Warning: one or more remote connections use a cleartext http:// URL: <%=h(StringUtils.join(cleartextConnections, ", "))%>. Credentials are sent unencrypted on every ETL run.
</p>
<% } %>

<br>
<%
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
            %><%=link("edit", RemoteQueryConnectionUrls.urlEditRemoteConnection(c, connectionMap.get(field)))%><%
            %><%=link("delete", RemoteQueryConnectionUrls.urlDeleteRemoteConnection(c, connectionMap.get(field)))%><%
            %><%=link("test", RemoteQueryConnectionUrls.urlTestRemoteConnection(c, connectionMap.get(field)))%><%
            %><%=h(connectionMap.get(field))%>
            <br/><%
        }
%> <p/>
<%
        if (hasAdminOpsPerm)
        {
            %><%=link("create new connection", RemoteQueryConnectionUrls.urlCreateRemoteConnection(c))%><%
        }
    }
%>
