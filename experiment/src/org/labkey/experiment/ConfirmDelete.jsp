<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.exp.api.ExpObject" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.api.security.SecurableResource" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.ConfirmDeleteView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ConfirmDeleteView.ConfirmDeleteBean> me = (JspView<ConfirmDeleteView.ConfirmDeleteBean>) HttpView.currentView();
    ConfirmDeleteView.ConfirmDeleteBean bean = me.getModelBean();
    Container currentContainer = getContainer();

    ActionURL successUrl = bean.getSuccessUrl();
    if (successUrl == null)
        successUrl = new ActionURL(ExperimentController.BeginAction.class, getContainer());

    ActionURL cancelUrl = bean.getCancelUrl();
    if (cancelUrl == null)
        cancelUrl = successUrl;
    int numReferencedItems = bean.getReferencedItems().size();
    int numObjects = bean.getObjects().size();
    int numNoPermissionExtras = bean.getNoPermissionExtras().size();
    int numWithPermission = bean.getRunsWithPermission().size();
%>

<% if (numReferencedItems > 0) { %>
    <span class="labkey-error">
        <%= h(numReferencedItems > 1 ? Integer.toString(numReferencedItems) : "One") %> <%= h(bean.getObjectType())%><%= h(numReferencedItems > 1 ? "s" : "") %> cannot be deleted because there are <%= h(bean.getReferencesDescription())%>:
    </span>
    <ul>
        <%  int count = 0;
            for (ExpObject item: bean.getReferencedItems()) {
                if (count >= 50)
                {
        %>(<%= numReferencedItems - count %> others omitted from list)<%
            break;
        }
        count++;
    %>
        <li>
            <a href="<%= h(item.detailsURL()) %>"><%= h(item.getName()) %></a>
        </li>
        <% } %>
    </ul>
<% } %>

<% if (bean.getObjects().isEmpty())
{
    %><p>There are no <%= h(numReferencedItems > 0 ? " additional " : "")%>selected objects to delete.</p>
    <%= button("OK").href(successUrl) %><%
}
else
{ %>
    <p>Are you sure you want to delete the following <%= h(bean.getObjectType()) %><%= h(numObjects > 1 ? (bean.getObjectType().endsWith("h") ? "es" : "s") : "") %>?</p>

    <ul>
    <% for (ExpObject object : bean.getObjects()) { %>
        <li><a href="<%= h(new ActionURL(bean.getDetailAction(), currentContainer).addParameter("rowId", object.getRowId())) %>"><%= h(object.getName()) %></a></li>
    <% } %>
    </ul>

    <% if (bean.getDeleteableExtras().size() > 0) { %>
        <%= h(bean.getDeleteableExtras().size() > 1 ? Integer.toString(bean.getDeleteableExtras().size()) : "One") %> <%= h(bean.getExtraNoun())%><%= h(bean.getDeleteableExtras().size() > 1 ? "s" : "") %> will also be deleted:

        <ul>
        <%  int count = 0;
            for (Pair<SecurableResource, ActionURL> entry : bean.getDeleteableExtras()) {
            if (count >= 50)
            {
                %>(<%= numWithPermission - count %> <%= h(bean.getExtraNoun()) %>s omitted from list)<%
                break;
            }
            count++;
            %>
            <li>
                <a href="<%=h(entry.getValue())%>"><%= h(entry.getKey().getResourceName()) %></a>
                <% if (!entry.getKey().getResourceContainer().equals(currentContainer))
                { %>
                    (in <a href="<%=h(entry.getKey().getResourceContainer().getStartURL(getUser()))%>"><%= h(entry.getKey().getResourceContainer().getPath()) %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>



<% if (numNoPermissionExtras > 0) { %>
    <span class="labkey-error">
        <%= h(numNoPermissionExtras > 1 ? Integer.toString(numNoPermissionExtras) : "One") %> <%= h(bean.getExtraNoun())%><%= h(bean.getDeleteableExtras().size() > 1 ? "s" : "") %> reference the <%= h(bean.getObjectType()) %>, but you do not have permission to delete them:
    </span>

    <ul>
    <%  int count = 0;
    for (Pair<SecurableResource, ActionURL> entry : bean.getNoPermissionExtras()) {
        if (count >= 50)
        {
            %>(<%= numNoPermissionExtras - count %> others omitted from list)<%
            break;
        }
        count++;
        %>
        <li>
            <a href="<%= h(entry.getValue()) %>"><%= h(entry.getKey().getResourceName()) %></a>
            <% if (!entry.getKey().getResourceContainer().equals(currentContainer))
            { %>
                (in <a href="<%=h(entry.getKey().getResourceContainer().getStartURL(getUser()))%>"><%= h(entry.getKey().getResourceContainer().getPath()) %></a>)
            <% } %>
        </li>
    <% } %>
    </ul>
<% } %>

    <% if (numWithPermission > 0) { %>
        <%= h(numWithPermission > 1 ? Integer.toString(numWithPermission) : "One") %> run<%= h(numWithPermission > 1 ? "s" : "") %> will also be deleted:

        <ul>
        <%  int count = 0;
            for (Map.Entry<ExpRun, Container> runEntry : bean.getRunsWithPermission().entrySet()) {
            ExpRun run = runEntry.getKey();
            Container runContainer = runEntry.getValue();
            if (count >= 50)
            {
                %>(<%= numWithPermission - count %> runs omitted from list)<%
                break;
            }
            count++;
            %>
            <li>
                <% ActionURL url = urlProvider(ExperimentUrls.class).getShowRunGraphURL(run); %>
                <a href="<%=h(url)%>"><%= h(run.getName()) %></a>
                <% if (!runContainer.equals(currentContainer))
                { %>
                    (in <a href="<%=h(runContainer.getStartURL(getUser()))%>"><%= h(runContainer.getPath()) %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

    <% if (bean.getRunsWithoutPermission().size() > 0) { %>
        <span class="labkey-error">The <%= h(bean.getObjectType()) %><%= h(numObjects > 1 ? "s" : "") %> are also referenced by the following
            run<%= h(bean.getRunsWithoutPermission().size() > 1 ? "s" : "") %>, which you do not have permission to delete:</span>

        <ul>
        <%  int count = 0;
        for (Map.Entry<ExpRun, Container> runEntry : bean.getRunsWithoutPermission().entrySet()) {
            ExpRun run = runEntry.getKey();
            Container runContainer = runEntry.getValue();
            if (count >= 50)
            {
                %>(<%= bean.getRunsWithoutPermission().size() - count %> runs omitted from list)<%
                break;
            }
            count++;
            %>
            <li>
                <% ActionURL url = urlProvider(ExperimentUrls.class).getShowRunGraphURL(run); %>
                <a href="<%=h(url)%>"><%= h(run.getName()) %></a>
                <% if (!runContainer.equals(currentContainer))
                { %>
                    (in <a href="<%=h(runContainer.getStartURL(getUser()))%>"><%= h(runContainer.getPath()) %></a>)
                <% } %>
            </li>
        <% } %>
        </ul>
    <% } %>

    <labkey:form action="<%=getViewContext().cloneActionURL().deleteParameters()%>" method="post">
        <%
            if (getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME) != null)
            {
                for (String selectedValue : getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME))
                { %>
                    <input type="hidden" name="<%= h(DataRegion.SELECT_CHECKBOX_NAME) %>" value="<%= h(selectedValue) %>" /><%
                }
            }
        %>
        <% if (bean.getSingleObjectRowId() != null) { %>
            <input type="hidden" name="singleObjectRowId" value="<%= bean.getSingleObjectRowId() %>" />
        <% }
        if (bean.getDataRegionSelectionKey() != null) { %>
            <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(bean.getDataRegionSelectionKey()) %>" />
        <% }
        if (bean.getSuccessUrl() != null)
        { %>
            <input type="hidden" name="<%=ActionURL.Param.successUrl%>" value="<%= h(bean.getSuccessUrl()) %>"/>
        <% } %>
        <input type="hidden" name="forceDelete" value="true"/>
        <% if (bean.getRunsWithoutPermission().isEmpty() && bean.getNoPermissionExtras().isEmpty() )
        { %>
            <%= button("Confirm Delete").submit(true).disableOnClick(true) %>
        <% } %>
        <%= button("Cancel").href(cancelUrl) %>
    </labkey:form>
<% } %>
