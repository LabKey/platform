<%
/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExpObject" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.ConfirmDeleteView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.data.DataRegion" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ConfirmDeleteView.ConfirmDeleteBean> me = (JspView<ConfirmDeleteView.ConfirmDeleteBean>) HttpView.currentView();
    ConfirmDeleteView.ConfirmDeleteBean bean = me.getModelBean();
    Container currentContainer = bean.getViewContext().getContainer();
%>

<% if (bean.getObjects().isEmpty())
{ %>
    <p>There are no selected objects to delete.</p>
<%= generateButton("OK", "begin.view")%>

<% }
else
{ %>
    <p>Are you sure you want to delete the following <%= bean.getObjectType() %><%= bean.getObjects().size() > 1 ? (bean.getObjectType().endsWith("h") ? "es" : "s") : "" %>?</p>

    <ul>
    <% for (ExpObject object : bean.getObjects()) { %>
        <li><a href="<%= bean.getDetailAction() %>.view?rowId=<%= object.getRowId() %>"><%= object.getName() %></a></li>
    <% } %>
    </ul>

    <% if (bean.getRunsWithPermission().size() > 0) { %>
        The following run<%= bean.getRunsWithPermission().size() > 1 ? "s" : "" %> will also be deleted:

        <ul>
        <% for (Map.Entry<ExpRun, Container> runEntry : bean.getRunsWithPermission().entrySet()) {
            ExpRun run = runEntry.getKey();
            Container runContainer = runEntry.getValue();
            %>
            <li>
                <% ActionURL url = new ActionURL("Experiment", "showRunGraph.view", runContainer); %>
                <a href="<%= url %>rowId=<%= Integer.toString(run.getRowId()) %>"><%= run.getName() %></a>
                <% if (!runContainer.equals(currentContainer))
                { %>
                    (in <a href="<%= runContainer.getStartURL(bean.getViewContext()) %>"><%= runContainer.getPath() %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

    <% if (bean.getRunsWithoutPermission().size() > 0) { %>
        <font class="labkey-error">The <%= bean.getObjectType() %><%= bean.getObjects().size() > 1 ? "s" : "" %> are also referenced by the following
            run<%= bean.getRunsWithoutPermission().size() > 1 ? "s" : "" %>, which you do not have permission to delete:</font>

        <ul>
        <% for (Map.Entry<ExpRun, Container> runEntry : bean.getRunsWithoutPermission().entrySet()) {
            ExpRun run = runEntry.getKey();
            Container runContainer = runEntry.getValue();
            %>
            <li>
                <% ActionURL url = new ActionURL("Experiment", "showRunGraph.view", runContainer); %>
                <a href="<%= url %>rowId=<%= Integer.toString(run.getRowId()) %>"><%= run.getName() %></a>
                <% if (!runContainer.equals(currentContainer))
                { %>
                    (in <a href="<%= runContainer.getStartURL(bean.getViewContext()) %>"><%= runContainer.getPath() %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

    <form action="<%= bean.getViewContext().getActionURL().getAction() %>.post" method="post">
        <%
            if (bean.getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME) != null)
            {
                for (String selectedValue : bean.getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME))
                { %>
                    <input type="hidden" name="<%= DataRegion.SELECT_CHECKBOX_NAME%>" value="<%= selectedValue %>" /><%
                }
            }
        %>
        <% if (bean.getSingleObjectRowId() != null) { %>
            <input type="hidden" name="singleObjectRowId" value="<%= bean.getSingleObjectRowId() %>" />
        <% }
        if (bean.getDataRegionSelectionKey() != null) { %>
            <input type="hidden" name="<%= DataRegionSelection.DATA_REGION_SELECTION_KEY %>" value="<%= bean.getDataRegionSelectionKey() %>" />
        <% }
        if (bean.getReturnURL() != null)
        { %>
            <input type="hidden" name="returnURL" value="<%= h(bean.getReturnURL()) %>"/>
        <% } %>
        <input type="hidden" name="forceDelete" value="true"/>
        <% if (bean.getRunsWithoutPermission().isEmpty() )
        { %>
            <%= generateSubmitButton("Confirm Delete") %>
        <% } %>
        <%= generateButton("Cancel", bean.getReturnURL() == null ? "begin.view" : bean.getReturnURL())%>
    </form>
<% } %>
