<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ page import="org.labkey.api.query.QueryForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% QueryForm form = (QueryForm) __form; %>
<labkey:errors />
<form method="POST" action="<%=form.urlFor(QueryAction.deleteQuery)%>">
<p>Are you sure you want to delete the query '<%=h(form.getQueryName())%>'?</p>
<labkey:button text="OK" /> <labkey:button text="Cancel" href="<%=form.getSchema().urlFor(QueryAction.begin)%>" />
</form>