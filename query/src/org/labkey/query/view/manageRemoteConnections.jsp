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
<h2>Manage Remote Connections</h2>
<p>
    Administrators can define external remote connections to alternate LabKey servers.
    This feature should be used with great care since, depending
    on your configuration, any user with access to the remote site could view and modify arbitrary data in your remote server.
</p>
<%
    Container c = getContainer();
    boolean isAdmin = getUser().isSiteAdmin();
%>

<br>

<%
    if (isAdmin)
    {
        final String CATEGORY = "remote-connections";
        String name = "Conn2";

        /*
        Map<String, String> map1 = PropertyManager.getWritableProperties(CATEGORY, true);
        map1.put(CATEGORY + ":" + name, name);
        PropertyManager.saveProperties(map1);

        map1 = PropertyManager.getWritableProperties(CATEGORY + ":" + name, true);
        map1.put("URL", "http://localhost:8080/labkey");
        map1.put("user", "gktaylor@labkey.com");
        map1.put("password", "");
        map1.put("container", "OConnor ETL");
        PropertyManager.saveProperties(map1);
        */

        Map<String, String> map = PropertyManager.getProperties(CATEGORY);
        for (String field : map.keySet())
        {
            //Map<String, String> props = PropertyManager.getProperties(CATEGORY + ":" + name);
            %> <labkey:link href="<%= QueryController.RemoteConnectionUrls.urlEditRemoteConnection(c, map.get(field))%>" text="edit"/> <%
            %> <labkey:link href="<%= QueryController.RemoteConnectionUrls.urlDeleteRemoteConnection(c, map.get(field))%>" text="delete"/> <%
            %> <%= h(map.get(field)) %> <br/> <%
        } %>
        <p/>
<labkey:link href="<%= QueryController.RemoteConnectionUrls.urlCreatetRemoteConnection(c) %>" text="create new connection"/> <%
    }
%>
