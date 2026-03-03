<%
/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.list.controllers.ListController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Objects" %>
<%@ page import="org.labkey.list.model.ListQuerySchema" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    ListController.ListDeletionForm form = (ListController.ListDeletionForm)getModelBean();
    Map<Container, List<ListDefinition>> definitions = new HashMap<>();
    Container currentContainer = form.getContainer();
    String currentPath = currentContainer.getPath();
    User user = form.getUser();
    ListQuerySchema listQuerySchema = new ListQuerySchema(user, currentContainer);

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
<p>Are you sure you want to delete all data in <%= h(currentPath) %> from the following Lists? This action cannot be undone and will result in an empty list.</p>

<% for (var entry : definitions.entrySet()) { %>
<div>
    Defined in <%= h(entry.getKey().getPath()) %>:
    <ul>
        <% for (var listDef : entry.getValue()) {
            TableInfo table = listQuerySchema.getTable(listDef.getName());
            long count = (table != null) ? new TableSelector(table).getRowCount() : 0;
        %>
        <li>
            <%= simpleLink(listDef.getName(), listDef.urlFor(ListController.GridAction.class, listDef.getContainer())) %>
            &mdash; <%= count %> row<%= h(count != 1 ? "s" : "") %> in <%= h(currentPath) %>
        </li>
        <% } %>
    </ul>
</div>
<% } %>
