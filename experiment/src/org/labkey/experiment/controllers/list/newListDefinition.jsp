<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="org.labkey.experiment.controllers.list.NewListForm" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% NewListForm form = (NewListForm) __form;
    Map<String, String> keyTypes = new TreeMap<String, String>();
    for (ListDefinition.KeyType type : ListDefinition.KeyType.values())
    {
        keyTypes.put(type.toString(), type.getLabel());
    }
%>
<labkey:errors />
<form action="<%=h(urlFor(ListController.NewListDefinitionAction.class))%>" method="POST">
    <p>What is the name of your list?<br>
        <input type="text" id="ff_name" name="ff_name" value="<%=h(form.ff_name)%>"/>
    </p>
    <p>
        Every item in a list has a key value which uniquely identifies that item.
        What is the data type of the key in your list?<br>
        <select name="ff_keyType" >
            <labkey:options value="<%=form.ff_keyType%>" map="<%=keyTypes%>" />
        </select>
    </p>
    <p>
        What is the name of the key in your list?<br>
        <input type="text" name="ff_keyName" value="<%=h(form.ff_keyName)%>"/>
    </p>
    <labkey:button text="Create List" />
    <labkey:button text="Cancel" href="<%=urlFor(ListController.BeginAction.class)%>"/>
</form>
