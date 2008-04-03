<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ page import="org.labkey.api.query.QueryForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% QueryForm form = (QueryForm) getForm(); %>
<%=formatErrorsForPath("form")%>
<input type=hidden name=schemaName value="<%=h(form.getSchemaName())%>"><input type=hidden name=queryName value="<%=h(form.getQueryName())%>">
<p>Are you sure you want to delete the query '<%=h(form.getQueryName())%>'?</p>
