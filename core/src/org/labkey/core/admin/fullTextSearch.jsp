<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
%>
<form name="fullTextSearch" method="POST" action=folderSettings.post?tabId=fullTextSearch>
    <table>
        <tr>
            <td>
                The full-text search feature will search content in all folders where the user has read permissions.  There<br>
                may be cases when content that can be read should be excluded from multi-folder searches, for example, if the<br>
                folder contains archived versions of content and you want only the more recent versions of that content to<br>
                appear in search results. Uncheck the box to exclude this folder's content from multi-folder searches.<br>
            </td>
        </tr>
        <tr>
            <td>
                <label>
                    <input type="checkbox" id="searchable" name="searchable" <%=c.isSearchable() ? "checked='true'" : ""%>>
                Include this folder's content in multi-folder search results</label>
                <input type="hidden" name="<%=SpringActionController.FIELD_MARKER%>searchable">
            </td>
        </tr>
    </table>
    <%=PageFlowUtil.generateSubmitButton("Save")%>
</form>
