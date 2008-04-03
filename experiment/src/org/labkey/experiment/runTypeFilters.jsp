<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.exp.ExperimentRunFilter" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%
JspView<Set<ExperimentRunFilter>> me = (JspView<Set<ExperimentRunFilter>>) HttpView.currentView();

for (ExperimentRunFilter filter : me.getModelBean())
{ %>
    <a href="<%= ExperimentController.ExperimentUrlsImpl.get().getShowRunsURL(me.getViewContext().getContainer(), filter) %>"><%= filter.getDescription() %></a><br/>    
<%
}
%>