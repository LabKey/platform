<%@ page import="org.labkey.query.controllers.DbUserSchemaForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% DbUserSchemaForm form = (DbUserSchemaForm) __form;
    ActionURL urlCancel = new ActionURL("query", "begin", getContainer());
    urlCancel.addParameter(QueryParam.schemaName.toString(), form.getBean().getUserSchemaName());

%>
<form method="POST" action="adminDeleteDbUserSchema.view?dbUserSchemaId=<%=form.getBean().getDbUserSchemaId()%>">
<p> Are you sure you want to delete the schema '<%=h(form.getBean().getUserSchemaName())%>'?  The tables and queries defined
in this schema will no longer be accessible.
</p>
    <labkey:button text="OK" />
    <labkey:button text="Cancel" href="<%=urlCancel%>" />
</form>