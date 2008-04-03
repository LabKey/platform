<%@ page import="org.labkey.query.controllers.InternalViewForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% InternalViewForm form = (InternalViewForm) __form;
    ActionURL urlCancel = new ActionURL("query", "manageViews", getContainer());
    CstmView view = form.getViewAndCheckPermission();
    ActionURL urlPost = new ActionURL("query", "internalDeleteView", getContainer());
    urlPost.addParameter("customViewId", Integer.toString(form.getCustomViewId()));
%>
<form method="POST" action="<%=h(urlPost)%>">
    <p>Are you sure you want to delete this view?</p>
    <p>Schema: <%=h(view.getSchema())%><br>
       Query: <%=h(view.getQueryName())%><br>
        View Name: <%=h(view.getName())%><br>
        Owner: <%=h(String.valueOf(view.getCustomViewOwner()))%>
    </p>
    <labkey:button text="OK" /> <labkey:button text="Cancel" href="<%=urlCancel%>" />
</form>