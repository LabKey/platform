<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.pipeline.browse.BrowseFile" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.pipeline.browse.BrowseForm.Param" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="org.labkey.api.pipeline.browse.FileFilter" %>
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
<table cellspacing="2" cellpadding="0">
    <%
        ActionURL urlBrowse = getUrlBrowsePath();
        String urlOpenFolder = request.getContextPath() + "/Pipeline/images/folder_open.gif";
        String urlClosedFolder = request.getContextPath() + "/Pipeline/images/folder.gif";
        String urlFile = request.getContextPath() + "/Pipeline/images/file.gif";
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
        <td><a href="<%=h(urlBrowse)%>"><img src="<%=h(urlOpenFolder)%>" border="0" alt=""></a></td>
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
        <td><a href="<%=h(urlBrowse)%>"><img src="<%=h(urlClosedFolder)%>" border="0" alt=""></a></td>
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
