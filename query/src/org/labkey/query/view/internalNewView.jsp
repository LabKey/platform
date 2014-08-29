<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.controllers.InternalNewViewForm" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    InternalNewViewForm form = (InternalNewViewForm) HttpView.currentModel();
    ActionURL urlPost = new ActionURL(InternalNewViewAction.class, getContainer());
    ActionURL urlCancel = new ActionURL(ManageViewsAction.class, getContainer());
%>
<labkey:errors />
<labkey:form method="POST" action="<%=h(urlPost)%>">
    <p>Create New Custom View</p>
    <p>Schema Name: <br><input type="text" name="ff_schemaName" value="<%=h(form.ff_schemaName)%>"></p>
    <p>Query Name: <br><input type="text" name="ff_queryName" value="<%=h(form.ff_queryName)%>"></p>
    <p>View Name:<br><input type="text" name="ff_viewName" value="<%=h(form.ff_viewName)%>"></p>
    <p><input type="checkbox" name="ff_share" value="true"<%=checked(form.ff_share)%>> Share with other users</p>
    <p><input type="checkbox" name="ff_inherit" value="true"<%=checked(form.ff_share)%>> Inherit view in sub-projects</p>
    <labkey:button text="Create" /> <labkey:button text="Cancel" href="<%=h(urlCancel)%>" />
</labkey:form>
