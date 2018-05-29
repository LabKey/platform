<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExperimentController.ExclusionEventForm> me = (JspView<ExperimentController.ExclusionEventForm>) HttpView.currentView();
    ExperimentController.ExclusionEventForm bean = ((JspView<ExperimentController.ExclusionEventForm>)HttpView.currentView()).getModelBean();
%>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(ExperimentController.ExcludeRowsAction.class))%>" method="POST">
    <div>
        <p>These rows will be marked for exclusion from this run. </p>

        <label for="exclude-comment" style="display: block;">Comment</label>
        <textarea style="display: block; margin-bottom: 10px;: " rows="4" cols="50" name="comment" id="exclude-comment"></textarea>
        <labkey:button text="Confirm" submit="true"/>
        <labkey:button text="Cancel" href="<%=h(bean.getReturnURL())%>"/>
    </div>

    <input type="hidden" name="update" value="true" />
    <input type="hidden" name="runId" value="<%= bean.getRunId() %>" />
    <%= generateReturnUrlFormField(bean) %>
    <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(bean.getDataRegionSelectionKey()) %>" />
</labkey:form>
<br>
<br>
<% me.include(bean.getQueryView(), out); %>


