<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<DataIntegrationController.DeleteDefinitionsForm> me =
            (JspView<DataIntegrationController.DeleteDefinitionsForm>) HttpView.currentView();
    DataIntegrationController.DeleteDefinitionsForm form = me.getModelBean();

    List<String> disabledSelected = form.getSelectedNames(false);
    List<String> enabledSelected = form.getSelectedNames(true);
    ActionURL successUrl = form.getSuccessActionURL(getContainer().getStartURL(getUser()));
    ActionURL cancelUrl = form.getCancelActionURL(successUrl);
%>
<labkey:errors/>

<% if (null == form.getEtlDefIds() || form.getEtlDefIds().isEmpty()) { %>
    <p>There are no selected ETL definitions to delete.</p>
    <%= text(button("OK").href(successUrl).toString())%>
<% } else { %>
    <p>Are you sure you want to delete the following ETL definition<%=h(form.getEtlDefIds().size()!=1 ? "s" : "")%>? This action cannot be reversed.</p>
    <% if (!disabledSelected.isEmpty()) { %>
    <ul id="unscheduledEtls">
        <% for (String name : disabledSelected) { %>
            <li><%=h(name)%></li>
        <%} %>
    </ul>
    <br/>
    <% } %>
    <% if (!enabledSelected.isEmpty()) {
        String enabledWarnText = "Warning: ";
        if (enabledSelected.size() == 1)
            enabledWarnText += "This definition has been enabled and is scheduled to run. If it is deleted, it will also be unscheduled and removed from any future runs.";
        else
            enabledWarnText += "These definitions have been enabled and are scheduled to run. If they are deleted, they will also be unscheduled and removed from any future runs.";
     %>
    <p><%=h(enabledWarnText)%></p>
    <ul id="scheduledEtls">
        <% for (String name : enabledSelected) { %>
            <li><%=h(name)%></li>
        <%} %>
    </ul>
    <br/>
    <% } %>
    <labkey:form  method="POST">
        <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(form.getDataRegionSelectionKey()) %>" />
        <input type="hidden" name="etlDefIds" value="<%=form.getEtlDefIds()%>"/>
        <input type="hidden" name="confirmed" value="true"/>
        <input type="hidden" name="returnUrl" value="<%=cancelUrl%>"/>
        <%= button("Confirm Delete").submit(true) %>
        <%= text(button("Cancel").href(cancelUrl).toString())%>
    </labkey:form>
<% } %>