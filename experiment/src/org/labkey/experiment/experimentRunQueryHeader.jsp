<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.exp.ExperimentRunFilter"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.experiment.ChooseExperimentTypeBean" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView<ChooseExperimentTypeBean> me = (JspView<ChooseExperimentTypeBean>) HttpView.currentView();
    ChooseExperimentTypeBean bean = me.getModelBean();
    ActionURL baseURL = bean.getUrl().clone().deleteParameters();
%>
<form method="get" action="<%= baseURL %>">
    <% for (Pair<String, String> params : bean.getUrl().getParameters())
    {
        if (!"experimentRunFilter".equals(params.getKey()))
        { %>
            <input type="hidden" name="<%= PageFlowUtil.filter(params.getKey())%>" value="<%= PageFlowUtil.filter(params.getValue())%>" />
    <%  }
    } %>
    Filter by run type: <select name="experimentRunFilter" onchange="form.submit()">
        <% for (ExperimentRunFilter filter : bean.getFilters()) { %>
            <option <% if (filter == bean.getSelectedFilter()) { %>selected <% } %> value="<%= filter.getDescription() %>"><%=
                filter.getDescription() %></option>
        <% } %>
    </select>
</form>
