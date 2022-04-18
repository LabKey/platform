<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.AbstractParticipantCategory"%>
<%@ page import="org.labkey.api.data.AbstractParticipantGroup" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.query.CustomView" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.api.study.CohortFilter" %>
<%@ page import="org.labkey.api.study.Params" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.model.CohortService" %>
<%@ page import="org.labkey.api.study.model.ParticipantGroupService" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page import="org.labkey.specimen.actions.ReportConfigurationBean" %>
<%@ page import="org.labkey.specimen.report.SpecimenVisitReportParameters" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportConfigurationBean> me = (JspView<ReportConfigurationBean>) HttpView.currentView();
    ReportConfigurationBean bean = me.getModelBean();
    Container container = getContainer();
    User user = getUser();
    boolean showCohorts = StudyService.get().showCohorts(container, user);
    Study study = StudyService.get().getStudy(container);
    List<? extends Cohort> cohorts = null;
    if (showCohorts)
        cohorts = CohortService.get().getCohorts(container, user);
    HtmlString optionLabelStyle = HtmlString.unsafe("text-align: left; padding: 5px 5px 0 5px;");
    Map<String, CustomView> views = bean.getCustomViews(getViewContext());

    List<? extends AbstractParticipantCategory> categories = ParticipantGroupService.get().getParticipantCategories(container, user);
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
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
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
        HtmlString rowClass;
        for (SpecimenVisitReportParameters factory : bean.getFactories(category))
        {
            rowClass = getShadeRowClass(formRowIndex++);
            String showHideSuffix = "_" + categoryIndex + "_" + formRowIndex + "_" + bean.getUniqueId();
            String formName = "form" + showHideSuffix;
%>
    <tr><td>
    <labkey:form action="<%=new ActionURL(factory.getAction(), container)%>" name="<%=formName%>" method="GET">
        <%
            if (bean.isListView())
            {
        %>
            <div class="<%=rowClass%>">
                <table style="width: 100%;">
                    <tr>
                        <td style="padding-right: 10px;"><%= h(factory.getLabel())%></td>
                        <td style="text-align: right;">
                            <%=link("show options").href("#").onClick("return showOrHide('" + showHideSuffix + "')").id("showOptionsLink" + showHideSuffix)%>
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
                <span id="reportParameters<%= unsafe(showHideSuffix) %>" style="display:<%= text(bean.isListView() ? "none" : "block") %>; padding: 10px 0;">
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
                            <select name="<%= Params.cohortId %>" class="form-control">
                                <option value="">All Cohorts</option>
                            <%
                                for (Cohort cohort : cohorts)
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
                            <select name="<%= Params.cohortFilterType %>" class="form-control">
                            <%
                                for (CohortFilter.Type type : CohortFilter.Type.values())
                                {
                            %>
                                <option value="<%=type%>"<%=selected(type == selectedCohortType)%>>
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
                        <input type="hidden" name="<%= Params.cohortFilterType %>" value="<%= CohortFilter.Type.PTID_CURRENT %>">
                <%
                        }
                    }

                    if (showParticipantGroups && factory.allowsParticipantGroupFilter())
                    {
                %>
                        <tr>
                            <td style="<%= optionLabelStyle %>"><%= h(study.getSubjectNounSingular()) %> Group</td>
                            <td>
                                <select name="participantGroupFilter" class="form-control">
                                    <option value="">All Groups</option>
                                    <%
                                        for (AbstractParticipantCategory cat : categories)
                                        {
                                            AbstractParticipantGroup[] groups = cat.getGroups();
                                            if (null != groups)
                                            {
                                                for (AbstractParticipantGroup grp : groups)
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
                            <select name="statusFilterName" class="form-control">
                            <%
                                for (SpecimenVisitReportParameters.Status status : SpecimenVisitReportParameters.Status.values())
                                {
                            %>
                                <option value="<%=status%>"<%=selected(factory.getStatusFilter() == status)%>>
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
                        HtmlString viewPickerHtml = factory.getCustomViewPicker(views);
                %>
                    <tr>
                        <td style="<%= optionLabelStyle %>">Base</td>
                        <td>
                            <%= viewPickerHtml %>
                        </td>
                    </tr>
                <%
                    }

                    // AdditionalFormInputs values are html generated in classes that extend SpecimenVisitReportParameters
                    List<Pair<String, HtmlString>> additionalFormInputs = factory.getAdditionalFormInputHtml(user);
                    for (Pair<String, HtmlString> inputPair : additionalFormInputs)
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
                            <label>
                                <input type="checkbox" name="hideEmptyColumns"<%=checked(factory.isHideEmptyColumns())%>> Hide Empty Columns
                            </label>
                            <br>
                            <label>
                                <input type="checkbox" name="viewVialCount"<%=checked(!atLeastOneChecked || factory.isViewVialCount())%>> Vial Counts
                            </label>
                            <br>
                            <label>
                                <input type="checkbox" name="viewVolume"<%=checked(factory.isViewVolume())%>> Total Volume
                            </label>
                            <br>
                <%
                    if (factory.allowsParticipantAggregates())
                    {
                %>
                            <label>
                                <input type="checkbox" name="viewParticipantCount"<%=checked(factory.isViewParticipantCount())%>>
                                <%= h(StudyService.get().getSubjectNounSingular(container)) %> Counts
                            </label>
                            <br>
                            <label>
                                <input type="checkbox" name="viewPtidList"<%=checked(factory.isViewPtidList())%>>
                                <%= h(StudyService.get().getSubjectColumnName(container)) %>  List
                            </label>
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
