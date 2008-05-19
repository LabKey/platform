<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.query.controllers.DbUserSchemaForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% DbUserSchemaForm form = (DbUserSchemaForm) __form;
    ActionURL urlCancel = new ActionURL("query", "begin", getContainer());
    urlCancel.addParameter(QueryParam.schemaName.toString(), form.getBean().getUserSchemaName());

%>
<form method="POST" action="adminDeleteDbUserSchema.view?dbUserSchemaId=<%=form.getBean().getDbUserSchemaId()%>">
<p> Are you sure you want to delete the schema '<%=h(form.getBean().getUserSchemaName())%>'?  The tables and queries defined
in this schema will no longer be accessible.
</p>
    <labkey:button text="OK" />
    <labkey:button text="Cancel" href="<%=urlCancel%>" />
</form>