<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.list.ListDefinition.DiscussionSetting" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.experiment.controllers.list.EditListDefinitionForm" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<% EditListDefinitionForm form = (EditListDefinitionForm) __form;
    ListDefinition list = form.getList();
    Map<String, String> titleColumnOptions = new LinkedHashMap<String, String>();
    titleColumnOptions.put(null, "<AUTO>");
    titleColumnOptions.put(list.getKeyName(), list.getKeyName());
    for (DomainProperty property : list.getDomain().getProperties())
    {
        if (!titleColumnOptions.containsKey(property.getName()))
        {
            titleColumnOptions.put(property.getName(), property.getName());
        }
    }
%>
<labkey:errors />
<form action="<%=list.urlFor(ListController.Action.editListDefinition)%>" method="POST">
<table>
    <tr><th colspan="2">List Definition</th></tr>
    <tr><td>Name:</td><td><%=h(list.getName())%></td></tr>
    <tr><td>Description:</td><td>
        <textarea rows="5" cols="40" name="ff_description"><%=h(form.ff_description)%></textarea></tr> 
    <tr><td>Key Type:</td><td><%=h(list.getKeyType().getLabel())%></td></tr>
    <tr><td>Key Name:</td><td><input type="text" name="ff_keyName" value="<%=h(form.ff_keyName)%>"></td></tr>
    <tr><td>Title Field:</td><td><select name="ff_titleColumn"><labkey:options value="<%=list.getTitleColumn()%>" map="<%=titleColumnOptions%>" /></select></td></tr>
    <tr><td>Discussion Links:</td><td>
        <input type="radio" name="ff_discussionSetting" value="<%=DiscussionSetting.None.getValue()%>"<%=(DiscussionSetting.None == form.ff_discussionSetting ? " checked" : "")%>><%=DiscussionSetting.None.getText()%>
        <input type="radio" name="ff_discussionSetting" value="<%=DiscussionSetting.OnePerItem.getValue()%>"<%=(DiscussionSetting.OnePerItem == form.ff_discussionSetting ? " checked" : "")%>><%=DiscussionSetting.OnePerItem.getText()%>
        <input type="radio" name="ff_discussionSetting" value="<%=DiscussionSetting.ManyPerItem.getValue()%>"<%=(DiscussionSetting.ManyPerItem == form.ff_discussionSetting ? " checked" : "")%>><%=DiscussionSetting.ManyPerItem.getText()%>
    </td></tr>
    <tr><td>Allow Delete:</td><td><input type="checkbox" name="ff_allowDelete"<%=form.ff_allowDelete ? " checked" : ""%>></td></tr>
    <tr><td>Allow Import:</td><td><input type="checkbox" name="ff_allowUpload"<%=form.ff_allowUpload ? " checked" : ""%>></td></tr>
    <tr><td>Allow Export &amp; Print:</td><td><input type="checkbox" name="ff_allowExport"<%=form.ff_allowExport ? " checked" : ""%>></td></tr>
</table>
    <labkey:button text="Update" />
    <labkey:button text="Cancel" href="<%=list.urlShowDefinition()%>"/>
</form>