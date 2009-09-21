<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.DbUserSchemaDef" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="org.labkey.query.controllers.QueryControllerSpring" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p>
    Administrators can make database schemas accessible to LabKey Query.  This should be used with great care as it
    bypasses the security implemented by the modules.  This may allow any user with read access to this folder to see
    arbitrary data on the site.  Also, the database schema may change in future releases.  Queries written against
    this version of the database schema may stop working after an upgrade.
</p>
<% DbUserSchemaDef[] defs = QueryManager.get().getDbUserSchemaDefs(getContainer());
    if (defs.length == 0) { %>
<p>There are no database user schemas defined in this folder.</p>
<% }
else
{
String reloadedSchema = request.getParameter("reloadedSchema");
if (null != StringUtils.trimToNull(reloadedSchema))
    {
    %>
    <div class="labkey-error">
        Schema <%=h(reloadedSchema)%> was reloaded successfully.
    </div><br>
    <%
    }
    %>
<table>
    <%
    for (DbUserSchemaDef def : defs)
    {
        ActionURL urlEdit = new ActionURL(QueryControllerSpring.EditExternalSchemaAction.class, getContainer());
        urlEdit.addParameter("dbUserSchemaId", Integer.toString(def.getDbUserSchemaId()));
        ActionURL urlView = new ActionURL(QueryControllerSpring.SchemaAction.class, getContainer());
        urlView.addParameter("schemaName", def.getUserSchemaName());
        ActionURL urlReload = urlEdit.clone();
        urlReload.setAction(QueryControllerSpring.AdminReloadDbUserSchemaAction.class);
%>
        <tr>
            <td><a href="<%=urlView%>"><%=h(def.getUserSchemaName())%></a></td>
            <td><%if (getUser().isAdministrator()) {%><labkey:link text="edit" href="<%=urlEdit%>" /><%}%></td>
            <td><labkey:link text="reload" href="<%=urlReload%>" /></td>
        </tr>
    <% } %>
    </table>
<% } %>
<br>
<%if (getUser().isAdministrator()) {%><labkey:link href="<%= new ActionURL(QueryControllerSpring.InsertExternalSchemaAction.class, getContainer())%>" text="define new schema"/><%}%>
