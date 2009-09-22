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
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.study.CohortFilter" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ReportConfigurationBean> me = (JspView<SpringSpecimenController.ReportConfigurationBean>) HttpView.currentView();
    SpringSpecimenController.ReportConfigurationBean bean = me.getModelBean();
    Container container = me.getViewContext().getContainer();
    User user = me.getViewContext().getUser();
    boolean showCohorts = StudyManager.getInstance().showCohorts(container, user);
    Study study = StudyManager.getInstance().getStudy(container);
    CohortImpl[] cohorts = null;
    if (showCohorts)
        cohorts = StudyManager.getInstance().getCohorts(container, user);
    String optionLabelStyle = "text-align:right";
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
        return false;
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
        <%
            if (bean.isListView())
            {
        %>
            <tr class="<%= rowClass %>">
                <th style="text-align:right"><%= h(factory.getLabel())%></th>
                <td class="<%= rowClass %>">
                    [<a href="#" id="showOptionsLink<%= showHideSuffix %>" onclick="return showOrHide('<%= showHideSuffix %>');">show options</a>]
                <td valign="top" align="left" class="<%= rowClass %>">
                    <%= generateSubmitButton("View") %>
                </td>
            </tr>
        <%
            }
        %>
        <tr class="<%= rowClass %>">
            <td colspan="3">
                <span id="reportParameters<%= showHideSuffix %>" style="display:<%= bean.isListView() ? "none" : "block" %>">
                    <table>
                <%
                    if (showCohorts && factory.allowsCohortFilter())
                    {
                        Map<String, CohortFilter> cohortOptions = new LinkedHashMap<String, CohortFilter>();
                        if (study.isAdvancedCohorts())
                        {
                            for (CohortFilter.Type type : CohortFilter.Type.values())
                            {
                                for (CohortImpl cohort : cohorts)
                                {
                                    CohortFilter filter = new CohortFilter(type, cohort.getRowId());
                                    cohortOptions.put(type.getTitle() + " is " + cohort.getLabel(), filter);
                                }
                            }
                        }
                        else
                        {
                            for (CohortImpl cohort : cohorts)
                            {
                                CohortFilter filter = new CohortFilter(CohortFilter.Type.PTID_CURRENT, cohort.getRowId());
                                cohortOptions.put(cohort.getLabel(), filter);
                            }
                        }
                %>
                    <script type="text/javascript">
                        var cohortOptions = {};
                <%
                        for (Map.Entry<String, CohortFilter> option : cohortOptions.entrySet())
                        {
                %>
                        cohortOptions["<%= option.getKey() %>"] = { cohortId : <%= option.getValue().getCohortId() %>, cohortFilterType : '<%= option.getValue().getType().name() %>'};
                <%
                        }
                %>
                        function updateCohortInputs(selectedText)
                        {
                            var cohortId = cohortOptions[selectedText].cohortId;
                            var cohortFilterType = cohortOptions[selectedText].cohortFilterType;
                            document.forms['<%= formName %>']['<%= CohortFilter.Params.cohortId %>'].value = cohortId;
                            document.forms['<%= formName %>']['<%= CohortFilter.Params.cohortFilterType %>'].value = cohortFilterType;
                        }
                    </script>
                    <tr>
                        <td style="<%= optionLabelStyle %>">Cohort filter</td>
                        <td>
                            <input type="hidden" name="<%= CohortFilter.Params.cohortId %>" value="">
                            <input type="hidden" name="<%= CohortFilter.Params.cohortFilterType %>" value="">
                            <select onchange="updateCohortInputs(this.options[this.selectedIndex].text);">
                                <option value="">All Cohorts</option>
                                <%
                                    for (Map.Entry<String, CohortFilter> option : cohortOptions.entrySet())
                                    {
                                        String label = option.getKey();
                                        CohortFilter filter = option.getValue();
                                %>
                                    <option <%= factory.getCohortFilter() != null && factory.getCohortFilter().equals(filter) ? "SELECTED" : ""%>>
                                        <%= h(label) %>
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
                        <td style="<%= optionLabelStyle %>">Availability status</td>
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
                        <td style="<%= optionLabelStyle %>">Base view</td>
                        <td>
                            <%= viewPickerHtml %>
                        </td>
                    </tr>
                <%
                    }

                    List<Pair<String, String>> additionalFormInputs = factory.getAdditionalFormInputHtml();
                    for (Pair<String, String> inputPair : additionalFormInputs)
                    {
                %>
                    <tr>
                        <td style="<%= optionLabelStyle %>"><%= h(inputPair.getKey()) %></td>
                        <td><%= inputPair.getValue() %></td>
                    </tr>
                <%
                    }
                    boolean atLeastOneChecked = factory.isViewVialCount() ||
                            factory.isViewParticipantCount() ||
                            factory.isViewVolume() ||
                            factory.isViewPtidList();
                %>
                    <tr>
                        <td>&nbsp;</td>
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
                        <td colspan="2">
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