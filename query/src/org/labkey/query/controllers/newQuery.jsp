<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.query.QueryParam"%>
<%@ page import="org.labkey.query.controllers.NewQueryForm"%>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.query.controllers.QueryPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    NewQueryForm form = (NewQueryForm) __form;
    List<String> tableAndQueryNames = form.getSchema().getTableAndQueryNames(false);
%>
<labkey:errors />

<% if (tableAndQueryNames.size() == 0) { %>
    Cannot create a new query: no tables/queries exist in the current schema to base the new query on.
<% } else { %>
    <form action="<%=urlFor(QueryAction.newQuery)%>" method="POST">
        <input type="hidden" name="<%=QueryParam.schemaName%>" value="<%=form.getSchemaName()%>" />
        <input type="hidden" name="ff_redirect" id="ff_redirect" value="sourceQuery" />

        <p>What do you want to call the new query?<br>
            <input type="text" name="ff_newQueryName" value="<%=h(form.ff_newQueryName)%>">
        </p>

        <p>
            Which query/table do you want this new query to be based on?<br>
            <select name="ff_baseTableName">
                <% for (String queryName : tableAndQueryNames) { %>
                <option name="<%=h(queryName)%>"<%=queryName.equals(form.ff_baseTableName) ? " selected" : ""%>><%=h(queryName)%></option>
                <% } %>
            </select>
        </p>

        <labkey:button text="Create and design" onclick="document.getElementById('ff_redirect').value = 'designQuery'"/> <labkey:button text="Create and edit SQL" />
    </form>
<% } %>