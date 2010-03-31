<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryController.ExternalSchemaBean bean = (QueryController.ExternalSchemaBean)HttpView.currentModel();
    ExternalSchemaDef def = bean.getSchemaDef();
    DbScope initialScope = null;

    try
    {
        initialScope = DbScope.getDbScope(def.getDataSource());
    }
    catch (Exception e)
    {
    }

    if (null == initialScope)
    {
        initialScope = DbScope.getLabkeyScope();
    }
%>
<labkey:errors/>
This page is not yet implemented.
<table>
<%
    for (DbScope scope : bean.getScopes())
    { %>
        <tr><td colspan="2"><%=h(scope.getDisplayName())%></td></tr><%
        for (String schemaName : bean.getSchemaNames(scope, false))
        { %>
            <tr><td><%=h(schemaName)%></td><td><input value="<%=h(schemaName)%>"/></td></tr><%        
        }
    }
%>
</table>
