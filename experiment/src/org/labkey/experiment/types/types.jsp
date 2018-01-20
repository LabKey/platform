<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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
<%@ page import="org.labkey.api.exp.DomainDescriptor"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.experiment.types.TypesController.TypeBean" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.types.TypesController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TypeBean bean = (TypeBean)getModelBean();
%><h3>global types</h3><%
for (DomainDescriptor type : bean.globals.values())
{
    %><%=PageFlowUtil.unstyledTextLink(type.getName(), new ActionURL(TypesController.TypeDetailsAction.class, getContainer()).addParameter("type", type.getDomainURI()))%><br/><%
}
%><h3>local types</h3><%
for (DomainDescriptor type : bean.locals.values())
{
    %><%=PageFlowUtil.unstyledTextLink(type.getName(), new ActionURL(TypesController.TypeDetailsAction.class, getContainer()).addParameter("type", type.getDomainURI()))%><br/><%
}
%>
