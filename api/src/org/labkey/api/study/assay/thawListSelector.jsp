<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.study.assay.ThawListBean" %>
<%@ page import="org.labkey.api.study.assay.ThawListResolverType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ThawListBean> thisView = (JspView<ThawListBean>)HttpView.currentView();
    ThawListBean bean = thisView.getModelBean();
    RenderContext ctx = bean.getRenderContext();
    boolean listType = ThawListResolverType.LIST_NAMESPACE_SUFFIX.equals(ctx.get(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME));
    boolean textType = !listType;
%>
<table>
    <tr>
        <td><input type="radio" name="<%= ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME %>" <%= textType ? "checked='true'" : "" %> value="<%= ThawListResolverType.TEXT_NAMESPACE_SUFFIX %>" onClick="document.getElementById('ThawListDiv-List').style.display='none'; document.getElementById('ThawListDiv-TextArea').style.display='block';"></td>
        <td>Paste a lookup list as a TSV (tab-separated values)<%= helpPopup("Sample Lookup", "A lookup lets you assign a mapping from your own specimen numbers to participants and visits. The format is a tab-separated values (TSV), requiring the column 'Index' and using the values of the columns 'SpecimenID', 'ParticipantID', and 'VisitID'.<p>To use the template, fill in the values, select the entire spreadsheet (Ctrl-A), copy it to the clipboard, and paste it into the text area below.", true) %> [<a href="<%= request.getContextPath() %>/study/assay/SampleLookupTemplate.xls">download template</a>]</td>
    </tr>
    <tr>
        <td></td>
        <td><div id="ThawListDiv-TextArea" style="display:<%= textType ? "block" : "none" %>;"><textarea name="<%= ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME %>" rows="4" cols="50"><%= h(ctx.get(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME)) %></textarea></div></td>
    </tr>
    <tr>
        <td><input type="radio" name="<%= ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME %>" <%= listType ? "checked='true'" : "" %> value="<%= ThawListResolverType.LIST_NAMESPACE_SUFFIX %>" onClick="document.getElementById('ThawListDiv-List').style.display='block'; document.getElementById('ThawListDiv-TextArea').style.display='none';"></td>
        <td>Use an existing list<%= helpPopup("Sample Lookup", "A lookup lets you assign a mapping from your own specimen numbers to participants and visits. The target list must have your own specimen identifier as its primary key, and uses the values of the 'SpecimenID', 'ParticipantID', and 'VisitID' columns.") %></td>
    </tr>
    <tr>
        <td></td>
        <td>
            <input type="hidden" id="<%= ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME %>" name="<%= ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME %>" value="<%= h(ctx.get(ThawListResolverType.THAW_LIST_LIST_CONTAINER_INPUT_NAME)) %>"/>
            <input type="hidden" id="<%= ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME %>" name="<%= ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME %>" value="<%= h(ctx.get(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME)) %>"/>
            <input type="hidden" id="<%= ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME %>" name="<%= ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME%>" value="<%= h(ctx.get(ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME)) %>"/>
            <div id="ThawListDiv-List" style="display:<%= listType ? "block" : "none" %>;">
                <% include(bean.getListChooser(), out); %>
            </div>
        </td>
    </tr>
</table>