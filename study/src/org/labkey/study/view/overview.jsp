<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.CohortFilter" %>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.CohortManager" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.QCStateSet" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistic" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistics" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.text.NumberFormat" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyController.OverviewBean> me = (JspView<StudyController.OverviewBean>) HttpView.currentView();
    StudyController.OverviewBean bean = me.getModelBean();
    String contextPath = request.getContextPath();
    StudyImpl study = bean.study;
    Container container = study.getContainer();
    User user = (User) request.getUserPrincipal();
    StudyManager manager = StudyManager.getInstance();
    String visitsLabel = manager.getVisitManager(study).getPluralLabel();
    String subjectNoun = StudyService.get().getSubjectNounSingular(container);

    boolean showCohorts = CohortManager.getInstance().hasCohortMenu(container, user);
    CohortImpl selectedCohort = null;
    List<CohortImpl> cohorts = null;

    if (showCohorts)
    {
        selectedCohort = bean.cohortFilter != null ? bean.cohortFilter.getCohort(container, user) : null;
        cohorts = manager.getCohorts(container, user);
    }

    boolean showQCStates = manager.showQCStates(container);
    QCStateSet selectedQCStateSet = null;
    List<QCStateSet> qcStateSetOptions = null;

    if (showQCStates)
    {
        selectedQCStateSet = bean.qcStates;
        qcStateSetOptions = QCStateSet.getSelectableSets(container);
    }

    VisitStatistic[] statisticsToDisplay = bean.stats.toArray(new VisitStatistic[bean.stats.size()]);

    List<VisitImpl> visits = manager.getVisits(study, selectedCohort, user, Visit.Order.DISPLAY);
    List<DatasetDefinition> datasets = manager.getDatasetDefinitions(study, selectedCohort);
    boolean cantReadOneOrMoreDatasets = false;
    String basePage = buildURL(StudyController.OverviewAction.class);

    if (selectedCohort != null)
        basePage += "cohortId=" + selectedCohort.getRowId() + "&";
    if (selectedQCStateSet != null)
        basePage += "QCState=" + selectedQCStateSet.getFormValue() + "&";

%><%= text(bean.canManage ? textLink("Manage Study", ManageStudyAction.class) : "") %>
&nbsp;<%= textLink("Views", new ActionURL(ReportsController.BeginAction.class, container))%>&nbsp;
&nbsp;<%= textLink("Specimens", new ActionURL(SpecimenController.BeginAction.class, container))%>&nbsp;
<%
    boolean hasHiddenData = false;
    for (int i = 0; i < visits.size() && !hasHiddenData; i++)
        hasHiddenData = !visits.get(i).isShowByDefault();
    for (int i = 0; i < datasets.size() && !hasHiddenData; i++)
        hasHiddenData = !datasets.get(i).isShowByDefault();
    if (hasHiddenData)
    {
        String viewLink = bean.showAll ? textLink("Show Default Datasets", basePage) :
                textLink("Show All Datasets", basePage + "showAll=1");
        out.write(viewLink);
    }
%>
<labkey:form action="<%=h(buildURL(StudyController.OverviewAction.class))%>" name="changeFilterForm" method="GET">
    <input type="hidden" name="showAll" value="<%= text(bean.showAll ? "1" : "0") %>">
    <br><br>
    <%
        if (showCohorts)
        {
    %>
    <input type="hidden" name="<%= h(CohortFilterFactory.Params.cohortFilterType.name()) %>"
           value="<%= h(CohortFilter.Type.PTID_CURRENT.name()) %>">
    <%= h(subjectNoun) %>'s current cohort: <select name="<%= h(CohortFilterFactory.Params.cohortId.name()) %>"
                                                    onchange="document.changeFilterForm.submit()">
    <option value="">All</option>
    <%
        for (CohortImpl cohort : cohorts)
        {
    %>
    <option value="<%= cohort.getRowId() %>"<%=selected(selectedCohort != null && cohort.getRowId() == selectedCohort.getRowId()) %>>
        <%= h(cohort.getLabel()) %>
    </option>
    <%
        }
    %>
</select>
    <%
        }
        if (showQCStates)
        {
    %>
    QC State: <select name="<%= h(BaseStudyController.SharedFormParameters.QCState.name()) %>"
                      onchange="document.changeFilterForm.submit()">
    <%
        for (QCStateSet set : qcStateSetOptions)
        {
    %>
    <option value="<%= h(set.getFormValue()) %>"<%=selected(set.equals(selectedQCStateSet))%>>
        <%= h(set.getLabel()) %>
    </option>
    <%
        }
    %>
</select>
    <%
        }

        for (VisitStatistic stat : VisitStatistic.values())
        {
            boolean checked = bean.stats.contains(stat);
            out.print(text("<input name=\"visitStatistic\" value=\"" + h(stat.name()) + "\" type=\"checkbox\"" + checked(checked) + " onclick=\"document.changeFilterForm.submit()\">" + h(stat.getDisplayString(study)) + "\n"));
        }
    %>
</labkey:form>
<br><br>
<table id="studyOverview" class="labkey-data-region-legacy labkey-show-borders" style="border-collapse:collapse;">
    <tr class="labkey-alternate-row">
        <td class="labkey-column-header"><img alt="" width=60 height=1 src="<%=getWebappURL("_.gif")%>"></td>
        <td class="labkey-column-header"><%
            String slash = "";
            for (VisitStatistic v : statisticsToDisplay)
            {
        %><%=text(slash)%><%
            if (v == VisitStatistic.ParticipantCount)
            {
        %>All <%=h(visitsLabel)%><%
        }
        else
        {
        %><%=h(v.getDisplayString(study))%><%
                }
                slash = " / ";
            }
        %></td>
        <%

            for (VisitImpl visit : visits)
            {
                if (!bean.showAll && !visit.isShowByDefault())
                    continue;
                String label = visit.getDisplayString();
        %>
        <td class="labkey-column-header" align="center" valign="top">
            <%= h(label) %>
            <%= visit.getDescription() != null ? PageFlowUtil.helpPopup("Visit Description", visit.getDescription()) : "" %>
        </td>
        <%
            }
        %>
    </tr>
    <%
        int row = 0;
        VisitMapKey key = new VisitMapKey(0, 0);
        String prevCategory = null;
        boolean useCategories = false;

        for (DatasetDefinition dataset : datasets)
        {
            if (dataset.getCategory() != null)
            {
                useCategories = true;
                break;
            }
        }

        Map<VisitMapKey, Boolean> requiredMap = StudyManager.getInstance().getRequiredMap(study);

        for (DatasetDefinition dataset : datasets)
        {
            if (!bean.showAll && !dataset.isShowByDefault())
                continue;

            boolean userCanRead = dataset.canRead(user);

            if (!userCanRead)
                cantReadOneOrMoreDatasets = true;

            row++;
            key.datasetId = dataset.getDatasetId();

            if (useCategories)
            {
                String category = dataset.getCategory();
                if (category == null)
                    category = "Uncategorized";
                if (!category.equals(prevCategory))
                {
    %>
    <tr>
        <td class="labkey-highlight-cell" style="padding: 2px 5px;" align="left" colspan="<%= visits.size() + 2%>"><%= h(category) %>
        </td>
    </tr>
    <%
            }
            prevCategory = category;
        }

        String datasetLabel = (dataset.getLabel() != null ? dataset.getLabel() : "" + dataset.getDatasetId());
    %>
    <tr class="<%=getShadeRowClass(row % 2 == 0)%>">
        <td align="center" style="font-weight:bold;" category="<%= h(dataset.getCategory()) %>"><%= h(datasetLabel) %><%
            if (null != StringUtils.trimToNull(dataset.getDescription()))
            {
        %><%=PageFlowUtil.helpPopup(datasetLabel, dataset.getDescription())%><%
            }
        %></td>
        <td style="font-weight:bold;" align="center" nowrap="true"><%
            VisitStatistics all = new VisitStatistics();
            VisitStatistics stats;

            for (VisitImpl visit : visits)
            {
                key.visitRowId = visit.getRowId();
                stats = bean.visitMapSummary.get(key);

                if (null != stats)
                    for (VisitStatistic stat : VisitStatistic.values())
                        all.add(stat, stats.get(stat));
            }
            key.visitRowId = -1; // demographic?
            stats = bean.visitMapSummary.get(key);
            if (null != stats)
                for (VisitStatistic stat : VisitStatistic.values())
                    all.add(stat, stats.get(stat));

            String innerHtml = "";

            for (VisitStatistic stat : statisticsToDisplay)
            {
                if (!innerHtml.isEmpty())
                    innerHtml += " / ";

                innerHtml += NumberFormat.getInstance().format(all.get(stat));
            }

            if (userCanRead)
            {
                ActionURL defaultReportURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, container);
                defaultReportURL.addParameter(DatasetDefinition.DATASETKEY, dataset.getDatasetId());
                if (selectedCohort != null && bean.cohortFilter != null)
                    bean.cohortFilter.addURLParameters(study, defaultReportURL, "Dataset");
                if (bean.qcStates != null)
                    defaultReportURL.addParameter("QCState", bean.qcStates.getFormValue());

        %><a href="<%= h(defaultReportURL.getLocalURIString()) %>"><%=text(innerHtml)%>
        </a><%
        }
        else
        {
        %><%=text(innerHtml)%><%
            }
        %></td>
        <%

            for (VisitImpl visit : visits)
            {
                if (!bean.showAll && !visit.isShowByDefault())
                    continue;

                key.visitRowId = visit.getRowId();
                stats = bean.visitMapSummary.get(key);
                Boolean b = requiredMap.get(key);
                boolean isRequired = b == Boolean.TRUE;
                boolean isOptional = b == Boolean.FALSE;
                innerHtml = null;

                for (VisitStatistic stat : bean.stats)
                {
                    int count = null != stats ? stats.get(stat) : 0;

                    if (isRequired || isOptional || count > 0)
                        innerHtml = (null == innerHtml ? "" + count : innerHtml + " / " + count);
                }

                if (null == innerHtml)
                {
                    innerHtml = "&nbsp;";
                }
                else if (userCanRead)
                {
                    ActionURL datasetLink = new ActionURL(StudyController.DatasetAction.class, container);
                    datasetLink.addParameter(VisitImpl.VISITKEY, visit.getRowId());
                    datasetLink.addParameter(DatasetDefinition.DATASETKEY, dataset.getDatasetId());
                    if (selectedCohort != null)
                        bean.cohortFilter.addURLParameters(study, datasetLink, null);
                    if (bean.qcStates != null)
                        datasetLink.addParameter(BaseStudyController.SharedFormParameters.QCState, bean.qcStates.getFormValue());

                    innerHtml = "<a href=\"" + datasetLink.getLocalURIString() + "\">" + innerHtml + "</a>";
                }

        %>
        <td align="center" nowrap="true"><%=text(innerHtml)%>
        </td>
        <%
            }
        %></tr>
    <%
        }
    %>
</table>
<%
    if (cantReadOneOrMoreDatasets)
    {
%><span style="font-style: italic;">NOTE: You do not have read permission to one or more datasets.  Contact the study administrator for more information.</span><%
    }
%>
