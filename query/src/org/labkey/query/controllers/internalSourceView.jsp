<%@ page import="org.labkey.query.controllers.InternalSourceViewForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% InternalSourceViewForm form = (InternalSourceViewForm) __form;
    ActionURL urlPost = new ActionURL("query", "internalSourceView", getContainer());
    urlPost.addParameter("customViewId", Integer.toString(form.getCustomViewId()));
    ActionURL urlCancel = new ActionURL("query", "manageViews", getContainer());
    CstmView view = form.getViewAndCheckPermission();

%>
<labkey:errors />
<form method = "POST" action="<%=h(urlPost)%>">
    <p>Schema: <%=h(view.getSchema())%><br>
        Query: <%=h(view.getQueryName())%><br>
        Name: <%=h(view.getName())%><br>
        Owner: <%=h(String.valueOf(view.getCustomViewOwner()))%><br>
    </p>
    <table><tr><th>Columns</th><th>Filter/Sort</th></tr>
        <tr><td><textarea name="ff_columnList" rows="20" cols="40"><%=h(form.ff_columnList)%></textarea></td>
        <td><textarea name="ff_filter" rows="20" cols="40"><%=h(form.ff_filter)%></textarea></td>
        </tr>
    </table>

    <labkey:button text="Save" /> <labkey:button text="Cancel" href="<%=urlCancel%>" />
</form>