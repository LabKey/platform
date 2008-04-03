<%@ page import="org.labkey.api.exp.list.ListDefinition"%>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Map<String, String> props = part.getPropertyMap();

    Map<String, String> listOptions = new TreeMap<String, String>();
    Map<String, ListDefinition> lists = ListService.get().getLists(ctx.getContainer());
    for (String name : lists.keySet())
    {
        listOptions.put(String.valueOf(lists.get(name).getListId()), name);
    }
%>
This webpart displays data from a single list using its default view.  You can modify the default view on the grid page.<br><br>

If you want to let users change the list that's displayed or customize the view then use the query webpart.<br><br>

<form name="frmCustomize" method="post" action="<%=part.getCustomizePostURL(ctx.getContainer()).getEncodedLocalURIString()%>">
    <table>
        <tr>
            <td>Title:</td>
            <td><input type="text" name="title" width="60" value="<%=h(props.get("title"))%>"></td>
        </tr>
        <tr>
            <td>List:</td>
            <td>
                <select name="listId">
                    <labkey:options value="<%=props.get("listId")%>" map="<%=listOptions%>" />
                </select>
            </td>
        </tr>
        <tr>
            <td colspan="2"><labkey:button text="Submit"/></td>
        </tr>
    </table>
</form>