<%@ page import="org.labkey.query.controllers.PropertiesForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% PropertiesForm form = (PropertiesForm) __form; %>
<form method="POST" action="<%=form.urlFor(QueryAction.propertiesQuery)%>">
    <p>Name: <%=h(form.getQueryDef().getName())%></p>
    <p>Description:<br>
        <textarea name="ff_description" rows="5" cols="40"><%=h(form.ff_description)%></textarea>
    </p>
    <p>Should this query be available in child folders of this one?<br>
        <select name="ff_inheritable">
            <option value="true"<%=form.ff_inheritable ? " selected" : ""%>>Yes</option>
            <option value="false"<%=!form.ff_inheritable ? " selected" : ""%>>No</option>
        </select>
    </p>
    <p>Should this query be hidden from the user?<br>
        <select name="ff_hidden">
            <option value="true"<%=form.ff_hidden ? " selected" : ""%>>Yes</option>
            <option value="false"<%=!form.ff_hidden ? " selected" : ""%>>No</option>
        </select>
    </p>
    <labkey:button text="update" />
</form>