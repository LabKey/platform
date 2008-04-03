<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="org.labkey.experiment.controllers.list.ListDefinitionForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<% ListDefinitionForm form = (ListDefinitionForm) __form;
    ListDefinition list = form.getList();
%>
<form action="<%=list.urlFor(ListController.Action.deleteListDefinition)%>" method="POST">
    <p>Are you sure you want to delete the list '<%=h(list.getName())%>'?<br>
        <labkey:button text="OK" />
        <labkey:button text="Cancel" href="<%=list.urlFor(ListController.Action.showListDefinition)%>"/>
    </p>

</form>