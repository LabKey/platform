<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.samples.report.SpecimenVisitReportParameters" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
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
        int formRowIndex = 0;
        String rowClass;
        for (SpecimenVisitReportParameters factory : bean.getFactories(category))
        {
            rowClass = (formRowIndex++)%2==0 ? "labkey-alternate-row" : "labkey-row";
            String showHideSuffix = "_" + categoryIndex + "_" + formRowIndex;
            String formName = "form" + showHideSuffix;
%>
    <form action="<%=  new ActionURL(factory.getAction(), container).getLocalURIString() %>" name="<%= formName %>" method="GET">
        <tr class="<%= rowClass %>">
            <%
                if (bean.isListView())
                {
            %>
            <th style="text-align:right;vertical-align:top"><%= h(factory.getLabel())%></th>
            <%
                }
            %>
            <td class="<%= rowClass %>">
                <%
                    if (bean.isListView())
                    {
                %>
                [<a href="#" id="showOptionsLink<%= showHideSuffix %>" onclick="showOrHide('<%= showHideSuffix %>')">show options</a>]<br>
                <%
                    }
                %>
                <span id="reportParameters<%= showHideSuffix %>" style="display:<%= bean.isListView() ? "none" : "block" %>">
                    <table>
                <%
                    if (factory.allowsCohortFilter())
                    {
                %>
                    <tr>
                        <td>
                            <select name="<%= BaseStudyController.SharedFormParameters.cohortId.name() %>">
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
                    if (factory.allowsCustomViewFilter())
                    {
                        String viewPickerHtml = factory.getCustomViewPicker();
                %>
                    <tr>
                        <td>
                            <%= viewPickerHtml %>
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
                            <input type="checkbox" name="hideEmptyColumns" <%= factory.isHideEmptyColumns() ? "CHECKED" : "" %>> Hide Empty Columns<br>
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
                <% if (!bean.isListView())
                    {
                %>
                    <tr>
                        <td>
                        <%= buttonImg("Refresh",  "document['" + formName + "']['excelExport'].value=false;") %>
                        <%= buttonImg("Print View", "document['" + formName + "']['_print'].value=1; document['" + formName + "']['excelExport'].value=false;") %>
                        <%= buttonImg("Export to Excel", "document['" + formName + "']['excelExport'].value=true;") %>
                        </td>
                    </tr>
                <%
                    }
                %>
                </table>
                </span>
            </td>
            <%
                if (bean.isListView())
                {
            %>
            <td valign="top" align="left" class="<%= rowClass %>">
                <%= generateSubmitButton("View") %>
            </td>
            <%
                }
            %>
        </tr>
        <input type="hidden" name="_print" value="">
        <input type="hidden" name="excelExport" value="">
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