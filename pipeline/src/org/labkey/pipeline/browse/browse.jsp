<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.browse.BrowseFile" %>
<%@ page import="org.labkey.api.pipeline.browse.BrowseForm.Param" %>
<%@ page import="org.labkey.api.pipeline.browse.FileFilter" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.pipeline.browse.BrowseViewImpl.Page" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<% if (getForm().getFileFilterOptions().size() > 1)
{
    Map<String, String> map = new LinkedHashMap();
    for (Map.Entry<String, ? extends FileFilter> entry : getForm().getFileFilterOptions().entrySet())
    {
        map.put(entry.getKey(), entry.getValue().getLabel());
    }
%>
<p>Filter: <select name="<%=paramName(Param.fileFilter)%>" onchange="this.form.submit()">
    <labkey:options value="<%=getForm().getFileFilter()%>" map="<%=map%>" />
</select></p>
<% } %>
<table>
    <%
        ActionURL urlBrowse = getUrlBrowsePath();
        String urlOpenFolder = request.getContextPath() + "/pipeline/images/folder_open.gif";
        String urlClosedFolder = request.getContextPath() + "/pipeline/images/folder.gif";
        String urlFile = request.getContextPath() + "/pipeline/images/file.gif";
        for (int i = 0; i < parents.size(); i++)
        {
            Map.Entry<String, BrowseFile> entry = parents.get(i);
            urlBrowse.replaceParameter(paramName(Param.path), entry.getValue().getRelativePath());
    %>
    <tr>
        <% if (i > 0)
        { %>
        <td colspan="<%=i%>"></td>
        <% } %>
        <td></td>
        <td><a href="<%=h(urlBrowse)%>"><img src="<%=h(urlOpenFolder)%>" alt=""></a></td>
        <td colspan="<%=parents.size() - i + 1%>"><a href="<%=h(urlBrowse)%>"><%=h(entry.getKey())%></a></td>
    </tr>
    <% } %>
    <% for (BrowseFile bf : browseFiles) {
        %>
    <tr><td colspan="<%=parents.size()%>"></td>
        <td>
            <% if (!bf.isDirectory() || isDirectoriesSelectable()) { %>
                <input type="<%=isMultiSelect() ? "checkbox" : "radio"%>" name="<%=paramName(Param.file)%>" value="<%=h(bf.getRelativePath())%>"<%=isFileSelected(bf) ? " checked" : ""%>>
            <% } %>
        </td>
        <% if (bf.isDirectory()) {
            urlBrowse.replaceParameter(paramName(Param.path), bf.getRelativePath());
        %>
        <td><a href="<%=h(urlBrowse)%>"><img src="<%=h(urlClosedFolder)%>" alt=""></a></td>
        <td><a href="<%=h(urlBrowse)%>"><%=h(bf.getName())%></td>
        <% } else { %>
        <td><img src="<%=h(urlFile)%>" alt=""></td>
        <td>
            <%=h(bf.getName())%>
        </td>
        <% }%>
    </tr>
    <% } %>
</table>
<% if (isMultiSelect()) { %>
    <labkey:selectAll /> <labkey:clearAll />
<% } %>
<labkey:button text="<%=getForm().getActionText()%>" action="<%=getForm().getActionURL()%>" />
