<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="org.labkey.experiment.controllers.list.ListDefinitionForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<% ListDefinitionForm form = (ListDefinitionForm) __form;
    ListDefinition list = form.getList();
%>
<form action="<%=list.urlFor(ListController.Action.insert)%>" method="POST">
    <table>
        <tr>
            <td class="ms-searchform"><%=h(list.getKeyName())%></td>
            <td></td>
        </tr>
    </table>
</form>