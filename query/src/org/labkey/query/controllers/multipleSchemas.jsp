<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.query.controllers.QueryController.BaseExternalSchemaBean" %>
<%@ page import="org.labkey.query.controllers.QueryController.DataSourceInfo" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    BaseExternalSchemaBean bean = (BaseExternalSchemaBean)HttpView.currentModel();
    AbstractExternalSchemaDef def = bean.getSchemaDef();
    QueryController.DataSourceInfo initialSource = bean.getInitialSource();

%>
<labkey:errors/>
This page is not yet implemented.
<table>
<%
    Collection<QueryController.DataSourceInfo> sources = bean.getSources();
    for (DataSourceInfo source : sources)
    { %>
        <tr><td colspan="2"><%=h(source.sourceName)%></td></tr><%
        Collection<String> schemaNames = bean.getSchemaNames(source, false);
        for (String schemaName : schemaNames)
        { %>
            <tr><td><%=h(schemaName)%></td><td><input value="<%=h(schemaName)%>"/></td></tr><%        
        }
    }
%>
</table>
