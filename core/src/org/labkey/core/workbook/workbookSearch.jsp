<%
/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.CoreController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container container = getContainer();
%>
<table><tr>
<td style="padding-bottom: 20px">
<labkey:form method="GET" layout="inline" action="<%=new ActionURL(CoreController.LookupWorkbookAction.class, container)%>">
    <labkey:input label="Jump To Workbook:" type="text" placeholder="Enter ID" id="wbsearch-id" name="id" value=""/>
    <%= button("Go").submit(true) %>
</labkey:form>
</td><td style="padding-left:20px; padding-bottom: 20px">
<labkey:form method="GET" layout="inline" action='<%=new ActionURL("search", "search", container)%>'>
    <labkey:input label="Search Workbooks:" type="text" placeholder="Enter Text" id="wbtextsearch-id" name="q" size="40" value=""/>
    <labkey:input type="hidden" name="container" value="<%=h(container.getId())%>"/>
    <labkey:input type="hidden" name="includeSubfolders" value="1"/>
    <%= button("Search").submit(true) %>
</labkey:form>
</td></tr></table>
