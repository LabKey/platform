<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.controllers.InternalNewViewForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% InternalNewViewForm form = (InternalNewViewForm) __form;
    ActionURL urlPost = new ActionURL("query", "internalNewView", getContainer());
    ActionURL urlCancel = new ActionURL("query", "manageViews", getContainer());

%>
<labkey:errors />
<form method="POST" action="<%=h(urlPost)%>">
    <p>Create New Custom View</p>
    <p>Schema Name: <br><input type="text" name="ff_schemaName" value="<%=h(form.ff_schemaName)%>"></p>
    <p>Query Name: <br><input type="text" name="ff_queryName" value="<%=h(form.ff_queryName)%>"></p>
    <p>View Name:<br><input type="text" name="ff_viewName" value="<%=h(form.ff_viewName)%>"></p>
    <p><input type="checkbox" name="ff_share" value="true"<%=form.ff_share ? " checked" : ""%>> Share with other users</p>
    <p><input type="checkbox" name="ff_inherit" value="true"<%=form.ff_share ? " checked" : ""%>> Inherit view in sub-projects</p>
    <labkey:button text="Create" /> <labkey:button text="Cancel" href="<%=h(urlCancel)%>" />
</form>
