<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.list.controllers.ListController" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.ArrayList" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    ListController.ListDeletionForm form = (ListController.ListDeletionForm)getModelBean();
    Map<Container, List<ListDefinition>> definitions = new HashMap<>();

    form.getListContainerMap()
            .stream()
            .map(pair -> ListService.get().getList(pair.second, pair.first))
            .filter(Objects::nonNull)
            .forEach(listDef -> {
                var container = listDef.getContainer();
                if (!definitions.containsKey(container))
                    definitions.put(container, new ArrayList<>());
                definitions.get(container).add(listDef);
            });
%>
<labkey:errors></labkey:errors>
<p>Are you sure you want to delete the following Lists?</p>

<% for (var entry : definitions.entrySet()) { %>
<div>
    Defined in <%= h(entry.getKey().getPath()) %>:
    <ul>
        <% for (var listDef : entry.getValue()) { %>
        <li>
            <%= link(listDef.getName(), listDef.urlFor(ListController.GridAction.class, listDef.getContainer())).clearClasses() %>
        </li>
        <% } %>
    </ul>
</div>
<% } %>

