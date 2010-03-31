<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p>
    Administrators can make database schemas accessible to LabKey Query.  This should be used with great care as it
    bypasses the security implemented by the modules.  This may allow any user with read access to this folder to see
    arbitrary data on the site.  Also, the database schema may change in future releases.  Queries written against
    this version of the database schema may stop working after an upgrade.
</p>
<% ExternalSchemaDef[] defs = QueryManager.get().getExternalSchemaDefs(getContainer());
    if (defs.length == 0) { %>
<p>There are no database user schemas defined in this folder.</p>
<% }
else
{
String reloadedSchema = StringUtils.trimToNull(request.getParameter("reloadedSchema"));
if (null != reloadedSchema)
{
    %>
    <div class="labkey-error"><%

    if ("ALL".equals(reloadedSchema))
    { %>
        All schemas in this folder were reloaded successfully.<%
    }
    else
    { %>
        Schema <%=h(reloadedSchema)%> was reloaded successfully.<%
    } %>
    </div><br>
    <%
}
    %>
<table>
    <%
    for (ExternalSchemaDef def : defs)
    {
        ActionURL urlEdit = new ActionURL(QueryController.EditExternalSchemaAction.class, getContainer());
        urlEdit.addParameter("dbUserSchemaId", Integer.toString(def.getDbUserSchemaId()));
        ActionURL urlView = new ActionURL(QueryController.SchemaAction.class, getContainer());
        urlView.addParameter("schemaName", def.getUserSchemaName());
        ActionURL urlReload = urlEdit.clone();
        urlReload.setAction(QueryController.ReloadExternalSchemaAction.class);
%>
        <tr>
            <td><%=h(def.getUserSchemaName())%></td>
            <td><labkey:link text="view" href="<%=urlView%>" /></td>
            <td><%if (getUser().isAdministrator()) {%><labkey:link text="edit" href="<%=urlEdit%>" /><%}%></td>
            <td><labkey:link text="reload" href="<%=urlReload%>" /></td>
        </tr>
    <% } %>
    </table>
<% } %>
<br>
<%if (getUser().isAdministrator()) { %>
    <labkey:link href="<%= new ActionURL(QueryController.InsertExternalSchemaAction.class, getContainer())%>" text="define new schema"/>
    <labkey:link href="<%= new ActionURL(QueryController.InsertMultipleExternalSchemasAction.class, getContainer())%>" text="define multiple new schemas"/><%

    if (defs.length > 0)
    { %>
    <labkey:link href="<%= new ActionURL(QueryController.ReloadAllUserSchemas.class, getContainer())%>" text="reload all schemas"/><%
    }
    %>
<%}%>
