<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.list.list.ListController" %>
<%@ page import="org.labkey.list.list.NewListForm" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    NewListForm form = (NewListForm) __form;
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
    <p>
        Import from file <%=PageFlowUtil.helpPopup("Import from File", "Use this option if you have a spreadsheet that you would like uploaded as a list.")%>:
        <input type="checkbox" name="fileImport" <%=form.isFileImport() ? "checked" : "" %>>
    </p>
    <labkey:button text="Create List" />
    <labkey:button text="Cancel" href="<%=urlFor(ListController.BeginAction.class)%>"/>
</form>
