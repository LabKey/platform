<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.query.CustomView" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.CohortFilter" %>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.ParticipantCategoryImpl" %>
<%@ page import="org.labkey.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.specimen.report.SpecimenVisitReportParameters" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.ReportConfigurationBean> me = (JspView<SpecimenController.ReportConfigurationBean>) HttpView.currentView();
    SpecimenController.ReportConfigurationBean bean = me.getModelBean();
    Container container = getContainer();
    User user = getUser();
    boolean showCohorts = StudyManager.getInstance().showCohorts(container, user);
    Study study = StudyManager.getInstance().getStudy(container);
    List<CohortImpl> cohorts = null;
    if (showCohorts)
        cohorts = StudyManager.getInstance().getCohorts(container, user);
    String optionLabelStyle = "text-align: left; padding: 5px 5px 0 5px;";
    Map<String, CustomView> views = bean.getCustomViews(getViewContext());

    List<ParticipantCategoryImpl> categories = ParticipantGroupManager.getInstance().getParticipantCategories(container, user);
    boolean showParticipantGroups = categories != null && !categories.isEmpty();
%>
<%
    if (study == null)
    {
%>
This folder does not contain a study.
<%
    }
    else
    {
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
    FrameFactoryClassic.startTitleFrame(out, category, null, "100%", null); %>
<table cellspacing="0" cellpadding="3">
<%
        int formRowIndex = 0;
        _HtmlString rowClass;
        for (SpecimenVisitReportParameters factory : bean.getFactories(category))
        {
            rowClass = getShadeRowClass(formRowIndex++ %2 == 0);
            String showHideSuffix = "_" + categoryIndex + "_" + formRowIndex + "_" + bean.getUniqueId();
            String formName = "form" + showHideSuffix;
%>
    <tr><td>
    <labkey:form action="<%=new ActionURL(factory.getAction(), container)%>" name="<%=h(formName)%>" method="GET">
        <%
            if (bean.isListView())
            {
        %>
            <div class="<%=rowClass%>">
                <table style="width: 100%;">
                    <tr>
                        <td style="padding-right: 10px;"><%= h(factory.getLabel())%></td>
                        <td style="text-align: right;">
                            <%=textLink("show options", "#", "return showOrHide('" + showHideSuffix + "')", "showOptionsLink" + showHideSuffix)%>
                            <%= button("View").submit(true) %>
                        </td>
                    </tr>
                </table>
            </div>
        <%
            }
        %>
        <div class="<%=rowClass%>">
            <div>
                <span id="reportParameters<%= text(showHideSuffix) %>" style="display:<%= text(bean.isListView() ? "none" : "block") %>; padding: 10px 0;">
                <table>
                <%
                    if (showCohorts && factory.allowsCohortFilter())
                    {
                        CohortFilter.Type selectedCohortType = CohortFilter.Type.PTID_CURRENT;
                        int selectedCohortId = -1;
                        if (factory.getCohortFilter() != null)
                        {
                            selectedCohortType = factory.getCohortFilter().getType();
                            selectedCohortId = factory.getCohortFilter().getCohortId();
                        }
                %>
                    <tr>
                        <td style="<%= optionLabelStyle %>">Cohort filter</td>
                        <td>
                            <select name="<%= CohortFilterFactory.Params.cohortId %>">
                                <option value="">All Cohorts</option>
                            <%
                                for (CohortImpl cohort : cohorts)
                                {
                            %>
                                <option value="<%= cohort.getRowId() %>"<%=selected(cohort.getRowId() == selectedCohortId)%>>
                                    <%= h(cohort.getLabel()) %>
                                </option>
                            <%
                                }
                            %>
                            </select>
                        </td>
                    </tr>
                    <%
                        if (study.isAdvancedCohorts())
                        {
                    %>
                    <tr>
                        <td style="<%= optionLabelStyle %>">Cohort filter type</td>
                        <td>
                            <select name="<%= CohortFilterFactory.Params.cohortFilterType %>">
                            <%
                                for (CohortFilter.Type type : CohortFilter.Type.values())
                                {
                            %>
                                <option value="<%= type.name() %>"<%=selected(type == selectedCohortType)%>>
                                    <%= h(type.getTitle()) %>
                                </option>
                            <%
                                }
                            %>
                            </select>
                        </td>
                    </tr>
                <%
                        }
                        else
                        {
                %>
                        <input type="hidden" name="<%= CohortFilterFactory.Params.cohortFilterType %>" value="<%= CohortFilter.Type.PTID_CURRENT %>">
                <%
                        }
                    }

                    if (showParticipantGroups && factory.allowsParticipantGroupFilter())
                    {
                %>
                        <tr>
                            <td style="<%= optionLabelStyle %>"><%= h(study.getSubjectNounSingular()) %> Group</td>
                            <td>
                                <select name="participantGroupFilter">
                                    <option value="">All Groups</option>
                                    <%
                                        for (ParticipantCategoryImpl cat : categories)
                                        {
                                            ParticipantGroup[] groups = cat.getGroups();
                                            if (null != groups)
                                            {
                                                for (ParticipantGroup grp : groups)
                                                {
                                                    %>
                                                    <option value="<%= grp.getRowId() %>"<%=selected(grp.getRowId() == factory.getParticipantGroupFilter())%>>
                                                        <%
                                                            if (!grp.getLabel().equals(cat.getLabel()))
                                                            {
                                                        %>
                                                        <%= h(cat.getLabel() + " : " + grp.getLabel()) %>
                                                        <%
                                                        }
                                                            else
                                                        {
                                                        %>
                                                            <%= h(cat.getLabel()) %>
                                                        <%
                                                        }
                                                        %>
                                                    </option>
                                                    <%
                                                }
                                            }
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
                                <option value="<%= status.name() %>"<%=selected(factory.getStatusFilter() == status)%>>
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
                        String viewPickerHtml = factory.getCustomViewPicker(views);
                %>
                    <tr>
                        <td style="<%= optionLabelStyle %>">Base</td>
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
                            <input type="checkbox" name="hideEmptyColumns"<%=checked(factory.isHideEmptyColumns())%>> Hide Empty Columns<br>
                            <input type="checkbox" name="viewVialCount"<%=checked(!atLeastOneChecked || factory.isViewVialCount())%>> Vial Counts<br>
                            <input type="checkbox" name="viewVolume"<%=checked(factory.isViewVolume())%>> Total Volume<br>
                <%
                    if (factory.allowsParticipantAggregegates())
                    {
                %>
                            <input type="checkbox" name="viewParticipantCount"<%=checked(factory.isViewParticipantCount())%>>
                            <%= h(StudyService.get().getSubjectNounSingular(container)) %> Counts<br>
                            <input type="checkbox" name="viewPtidList"<%=checked(factory.isViewPtidList())%>>
                            <%= h(StudyService.get().getSubjectColumnName(container)) %>  List
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
                        <%= button("Refresh").submit(true).onClick("document['" + formName + "']['excelExport'].value=false;") %>
                        <%= button("Print").submit(true).onClick("document['" + formName + "']['_print'].value=1; document['" + formName + "']['excelExport'].value=false;") %>
                        <%= bean.hasReports() ? button("Export to Excel").submit(true).onClick("document['" + formName + "']['excelExport'].value=true;") :
                                button("Export to Excel").submit(true).onClick("document['" + formName + "']['excelExport'].value=true;").enabled(false) %>
                        </td>
                    </tr>
                <%
                    }
                %>
                </table>
                </span>
            </div>
        </div>
        <input type="hidden" name="_print" value="">
        <input type="hidden" name="excelExport" value="">
    </labkey:form>
    </td></tr>
<%
        }
%>
</table>
<%
        if (bean.isListView())
            FrameFactoryClassic.endTitleFrame(out);
    }
    }
%>
