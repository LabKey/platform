<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReportParameters" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.samples.report.specimentype.TypeSummaryReportFactory" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ReportConfigurationBean> me = (JspView<SpringSpecimenController.ReportConfigurationBean>) HttpView.currentView();
    SpringSpecimenController.ReportConfigurationBean bean = me.getModelBean();
    Container container = me.getViewContext().getContainer();
    User user = me.getViewContext().getUser();
    boolean showCohorts = StudyManager.getInstance().showCohorts(container, user);
    Cohort[] cohorts = null;
    if (showCohorts)
        cohorts = StudyManager.getInstance().getCohorts(container, user);
%>
<script type="text/javascript">
    function showOrHide(suffix)
    {
        var reportParameters = document.getElementById('reportParameters' + suffix);
        var showOptionsLink = document.getElementById('showOptionsLink' + suffix);
        if (reportParameters.style.display == "none")
        {
            reportParameters.style.display = "block";
            showOptionsLink.innerHTML = "hide options";
        }
        else
        {
            reportParameters.style.display = "none";
            showOptionsLink.innerHTML = "show options";
        }
    }
</script>
<%
    int categoryIndex = 0;
    for (String category : bean.getCategories())
    {
        categoryIndex++;
%>
<% if (bean.isListView())
    WebPartView.startTitleFrame(out, category, null, "100%", null); %>
<table cellspacing="0" cellpadding="3">
<%
        String shadeColor = "#EEEEEE";
        int formRowIndex = 0;
        String bgcolor;
        for (SpecimenVisitReportParameters factory : bean.getFactories(category))
        {
            bgcolor= (formRowIndex++)%2==0 ? shadeColor : "#FFFFFF";
            String showHideSuffix = "_" + categoryIndex + "_" + formRowIndex;
            String formName = "form" + showHideSuffix;
%>
    <form action="<%=  new ActionURL(factory.getAction(), container).getLocalURIString() %>" name="<%= formName %>" method="GET">
        <tr>
            <%
                if (bean.isListView())
                {
            %>
            <th align="right" valign="top" bgcolor="<%= bgcolor %>"><%= h(factory.getLabel())%></th>
            <%
                }
            %>
            <td bgcolor="<%= bgcolor %>">
                <%
                    if (bean.isListView())
                    {
                %>
                [<a href="#" id="showOptionsLink<%= showHideSuffix %>" onclick="showOrHide('<%= showHideSuffix %>')">show options</a>]<br>
                <%
                    }
                %>
                <span id="reportParameters<%= showHideSuffix %>" style="display:<%= bean.isListView() ? "none" : "block" %>">
                    <table cellspacing="0" cellpadding="2">
                <%
                    if (factory.allowsCohortFilter())
                    {
                %>
                    <tr>
                        <td>
                            <select name="cohortId">
                                <option value="">All Cohorts</option>
                                <%
                                    for (Cohort cohort : cohorts)
                                    {
                                %>
                                <option value="<%= cohort.getRowId() %>" <%= factory.getCohortId() != null &&
                                        factory.getCohortId() == cohort.getRowId() ? "SELECTED" : ""%>>
                                    <%= h(cohort.getLabel()) %>
                                </option>
                                <%
                                    }
                                %>
                            </select>
                        </td>
                    </tr>
                <%
                    }
                    if (factory.allowsAvailabilityFilter())
                    {
                %>
                    <tr>
                        <td>
                            <select name="statusFilterName">
                            <%
                                for (SpecimenVisitReportParameters.Status status : SpecimenVisitReportParameters.Status.values())
                                {
                            %>
                                <option value="<%= status.name() %>" <%= factory.getStatusFilter() == status ? "SELECTED" : "" %>>
                                    <%= h(status.getCaption()) %>
                                </option>
                            <%
                                }
                            %>
                            </select>
                        </td>
                    </tr>
                <%
                    }
                    List<String> additionalFormInputs = factory.getAdditionalFormInputHtml();
                    for (String html : additionalFormInputs)
                    {
                %>
                    <tr>
                        <td><%= html %></td>
                    </tr>
                <%
                    }
                    boolean atLeastOneChecked = factory.isViewVialCount() ||
                            factory.isViewParticipantCount() ||
                            factory.isViewVolume() ||
                            factory.isViewPtidList();
                %>
                    <tr>
                        <td>
                            <input type="checkbox" name="viewVialCount" <%= !atLeastOneChecked || factory.isViewVialCount() ? "CHECKED" : "" %>> Vial Counts<br>
                            <input type="checkbox" name="viewVolume" <%= factory.isViewVolume() ? "CHECKED" : "" %>> Total Volume<br>
                <%
                    if (factory.allowsParticipantAggregegates())
                    {
                %>
                            <input type="checkbox" name="viewParticipantCount" <%= factory.isViewParticipantCount() ? "CHECKED" : "" %>> Participant Counts<br>
                            <input type="checkbox" name="viewPtidList" <%= factory.isViewPtidList() ? "CHECKED" : "" %>> Participant ID List
                <%
                    }
                %>
                        </td>
                    </tr>
                </table>
                </span>
            </td>
            <td valign="top" align="left" bgcolor="<%= bgcolor %>">
                <%= buttonImg(bean.isListView() ? "View" : "Update") %>
                <% if (!bean.isListView())
                    {
                %>
                <br><%= buttonImg("Print View", "document['" + formName + "']['_print'].value=1;") %>
                <%
                    }
                %>
            </td>
        </tr>
        <input type="hidden" name="_print" value="">
    </form>
<%
        }
%>
</table>
<%
        if (bean.isListView())
            WebPartView.endTitleFrame(out);
    }
%>