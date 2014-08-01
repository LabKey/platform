<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ActionURL textURL = new ActionURL(SearchController.ExportIndexContentsAction.class, ContainerManager.getRoot()).addParameter("format", "Text");
    ActionURL excelURL = new ActionURL(SearchController.ExportIndexContentsAction.class, ContainerManager.getRoot()).addParameter("format", "Excel");
%>
<p>Click one of the buttons below to export details about the full-text search index in the specified format. Each export file
includes one row for each document in the index, listing details such as title, type, folder, url, unique id, and unique body
term count. Body term count is an approximate, relative measure of the size of this document in the index.</p>

<p>Use Excel or your favorite tool to sort, filter, and pivot the data. For example, sort by term count to identify the largest
documents in the index. Or sum body terms by folder path to determine folders contributing the most to index size.</p>

<p>Note: A file format warning message may appear when exporting to Excel; click "Yes" and the data should correctly appear.</p>
<%=button("Export to Text").href(textURL).build()%>
<%=button("Export to Excel").href(excelURL).build()%>
