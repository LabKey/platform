<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.qc.QCStateManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.specimen.SpecimenMigrationService" %>
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.api.study.CohortFilter" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Params" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.util.Link.LinkBuilder" %>
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.SingleCohortFilter" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController.SharedFormParameters" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DefaultDatasetReportAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.OverviewAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.OverviewBean" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController.BeginAction" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.CohortManager" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.QCStateSet" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="org.labkey.study.query.DatasetQueryView" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistic" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistics" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.labkey.study.model.QCStateSet.getQCUrlFilterKey" %>
<%@ page import="static org.labkey.study.model.QCStateSet.getQCStateFilteredURL" %>
<%@ page import="static org.labkey.study.model.QCStateSet.PUBLIC_STATES_LABEL" %>
<%@ page import="static org.labkey.study.model.QCStateSet.PRIVATE_STATES_LABEL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<OverviewBean> me = (JspView<OverviewBean>) HttpView.currentView();
    OverviewBean bean = me.getModelBean();
    StudyImpl study = bean.study;
    Container container = study.getContainer();
    User user = (User) request.getUserPrincipal();
    StudyQuerySchema schema = StudyQuerySchema.createSchema(study, user, null);
    StudyManager manager = StudyManager.getInstance();
    VisitManager visitManager = manager.getVisitManager(study);
    String visitsLabel = visitManager.getPluralLabel();
    String subjectNoun = StudyService.get().getSubjectNounSingular(container);
    String qcUrlFilterKey = getQCUrlFilterKey(CompareType.EQUAL, DatasetQueryView.DATAREGION);
    String visitLabelUrlFilterKey = visitManager.getVisitLabelUrlFilterKey(container, DatasetQueryView.DATAREGION);
    String visitLabelRowIdFilterKey = visitManager.getVisitRowIdUrlFilterKey(container, DatasetQueryView.DATAREGION);

    boolean showCohorts = CohortManager.getInstance().hasCohortMenu(container, user);
    Cohort selectedCohort = null;
    CohortFilter cohortFilter = null;
    Collection<CohortImpl> cohorts = null;

    if (showCohorts)
    {
        if (bean.cohortFilter != null)
        {
            selectedCohort = bean.cohortFilter.getCohort(container, user);
            // Get a cohort filter that includes the label, so we use the label (not the rowId) in the filter clause
            cohortFilter = new SingleCohortFilter(bean.cohortFilter.getType(), selectedCohort);
        }
        cohorts = manager.getCohorts(container, user);
    }

    boolean showQCStates = QCStateManager.getInstance().showStates(container);
    QCStateSet selectedQCStateSet = null;
    List<QCStateSet> qcStateSetOptions = null;

    if (showQCStates)
    {
        selectedQCStateSet = bean.qcStates;
        qcStateSetOptions = QCStateSet.getSelectableSets(container);
    }

    List<VisitImpl> visits = new ArrayList<>(manager.getVisits(study, selectedCohort, user, Visit.Order.DISPLAY));
    List<DatasetDefinition> datasets = manager.getDatasetDefinitions(study, selectedCohort);
    boolean cantReadOneOrMoreDatasets = false;
    ActionURL baseURL = urlFor(OverviewAction.class);

    if (selectedCohort != null)
        baseURL.addParameter("cohortId", selectedCohort.getRowId());
    if (selectedQCStateSet != null)
        baseURL.addParameter("QCState", selectedQCStateSet.getFormValue());

%><%=bean.canManage ? link("Manage Study", ManageStudyAction.class) : HtmlString.EMPTY_STRING%>
&nbsp;<%= link("Views", new ActionURL(BeginAction.class, container))%>&nbsp;
 <%= bean.showSpecimens ? link("Specimens", SpecimenMigrationService.get().getBeginURL(container)) : HtmlString.EMPTY_STRING%>
<%
    boolean hasHiddenData = false;
    for (int i = 0; i < visits.size() && !hasHiddenData; i++)
        hasHiddenData = !visits.get(i).isShowByDefault();
    for (int i = 0; i < datasets.size() && !hasHiddenData; i++)
        hasHiddenData = !datasets.get(i).isShowByDefault();
    if (hasHiddenData)
    {
        LinkBuilder viewLink = bean.showAll ? link("Show Default Datasets").href(baseURL) :
                link("Show All Datasets").href(baseURL.addParameter("showAll", "1"));
        out.print(viewLink);
    }
%>
<labkey:form action="<%=urlFor(OverviewAction.class)%>" name="changeFilterForm" method="GET">
    <input type="hidden" name="showAll" value="<%= unsafe(bean.showAll ? "1" : "0") %>">
    <br><br>
    <%
        if (showCohorts)
        {
            var selectId = makeId("select");
            addHandler(selectId, "change", "document.changeFilterForm.submit()");
    %>
    <input type="hidden" name="<%= h(Params.cohortFilterType.name()) %>"
           value="<%= h(CohortFilter.Type.PTID_CURRENT.name()) %>">
    <%= h(subjectNoun) %>'s current cohort: <%=select()
        .id(selectId)
        .name(Params.cohortId.name())
        .addOption("All", "")
        .addOptions(cohorts.stream().map(c -> new OptionBuilder(c.getLabel(), c.getRowId())))
        .selected(selectedCohort != null ? selectedCohort.getRowId() : null)
        .className(null)
    %>
    <%
        }
        if (showQCStates)
        {
            var selectId = makeId("select");
            addHandler(selectId, "change", "document.changeFilterForm.submit()");
    %>
    QC State: <%=select()
        .id(selectId)
        .name(SharedFormParameters.QCState.name())
        .addOptions(qcStateSetOptions.stream().map(qc -> new OptionBuilder(qc.getLabel(), qc.getFormValue())))
        .selected(selectedQCStateSet != null ? selectedQCStateSet.getFormValue() : null)
        .className(null)
    %>
    <%
        }

        for (VisitStatistic stat : VisitStatistic.values())
        {
            boolean checked = bean.stats.contains(stat);
            var id = makeId("visitStatistic");
            addHandler(id, "click", "document.changeFilterForm.submit();" );
            out.print(unsafe("<label><input id=\"" + id + "\" name=\"visitStatistic\" value=\"" + h(stat.name()) + "\" type=\"checkbox\"" + checked(checked) + ">" + h(stat.getDisplayString(study)) + "</label>\n"));
        }
    %>
</labkey:form>
<br><br>
<table id="studyOverview" class="labkey-data-region-legacy labkey-show-borders" style="border-collapse:collapse;">
    <tr class="labkey-alternate-row">
        <td class="labkey-column-header"><img alt="" width=60 height=1 src="<%=getWebappURL("_.gif")%>"></td>
        <td class="labkey-column-header"><%
            String slash = "";
            for (VisitStatistic v : bean.stats)
            {
        %><%=unsafe(slash)%><%
                if (v == VisitStatistic.ParticipantCount)
                {
        %><%=h(visits.isEmpty() ? subjectNoun + " Count" : "All " + visitsLabel)%><%
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
            <%= visit.getDescription() != null ? helpPopup("Visit Description", visit.getDescription()) : HtmlString.EMPTY_STRING %>
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

            TableInfo t = schema.getDatasetTable(dataset, null);
            boolean userCanRead = null != t && t.hasPermission(user, ReadPermission.class);

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
    <tr class="<%=getShadeRowClass(row)%>">
        <td align="center" style="font-weight:bold;" category="<%= h(dataset.getCategory()) %>"><%= h(datasetLabel) %><%
            if (null != StringUtils.trimToNull(dataset.getDescription()))
            {
        %><%=helpPopup(datasetLabel, dataset.getDescription())%><%
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

            HtmlStringBuilder innerHtml = HtmlStringBuilder.of();

            for (VisitStatistic stat : bean.stats)
            {
                if (!innerHtml.isEmpty())
                    innerHtml.append(" / ");

                innerHtml.append(NumberFormat.getInstance().format(all.get(stat)));
            }

            if (userCanRead)
            {
                ActionURL defaultReportURL = new ActionURL(DefaultDatasetReportAction.class, container);
                defaultReportURL.addParameter(Dataset.DATASET_KEY, dataset.getDatasetId());

                if (selectedCohort != null && cohortFilter != null)
                    cohortFilter.addURLParameters(study, defaultReportURL, DatasetQueryView.DATAREGION);
                if (bean.qcStates != null && StringUtils.isNumeric(bean.qcStates.getFormValue()))
                    defaultReportURL.replaceParameter(qcUrlFilterKey, QCStateManager.getInstance().getStateForRowId(container, Integer.parseInt(bean.qcStates.getFormValue())).getLabel());
                // Public States case
                if (bean.qcStates != null && QCStateSet.getPublicStates(getContainer()).getFormValue().equals(bean.qcStates.getFormValue()))
                    defaultReportURL = getQCStateFilteredURL(defaultReportURL, PUBLIC_STATES_LABEL, DatasetQueryView.DATAREGION, container);
                // Private States case
                if (bean.qcStates != null && QCStateSet.getPrivateStates(getContainer()).getFormValue().equals(bean.qcStates.getFormValue()))
                    defaultReportURL = getQCStateFilteredURL(defaultReportURL, PRIVATE_STATES_LABEL, DatasetQueryView.DATAREGION, container);
                defaultReportURL.addParameter("skipDataVisibility", 1);

                %><%=link(innerHtml, defaultReportURL).clearClasses()%><%
            }
            else
            {
                %><%=innerHtml%><%
            }
        %>
        </td>
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
                innerHtml = HtmlStringBuilder.of();

                for (VisitStatistic stat : bean.stats)
                {
                    int count = null != stats ? stats.get(stat) : 0;

                    if (isRequired || isOptional || count > 0)
                        innerHtml.append(innerHtml.isEmpty() ? "" + count : " / " + count);
                }

                if (innerHtml.isEmpty())
                {
                    innerHtml = HtmlStringBuilder.of(HtmlString.NBSP);
                }
                else if (userCanRead)
                {
                    ActionURL datasetLink = new ActionURL(DatasetAction.class, container);
                    if (StringUtils.isBlank(visit.getLabel()))
                        datasetLink.addParameter(visitLabelRowIdFilterKey, visit.getRowId());
                    else
                        datasetLink.addParameter(visitLabelUrlFilterKey, visit.getLabel());
                    datasetLink.addParameter(Dataset.DATASET_KEY, dataset.getDatasetId());
                    if (selectedCohort != null)
                        cohortFilter.addURLParameters(study, datasetLink, DatasetQueryView.DATAREGION);
                    if (bean.qcStates != null && StringUtils.isNumeric(bean.qcStates.getFormValue()))
                        datasetLink.replaceParameter(qcUrlFilterKey, QCStateManager.getInstance().getStateForRowId(container, Integer.parseInt(bean.qcStates.getFormValue())).getLabel());

                    innerHtml = HtmlStringBuilder.of(link(innerHtml, datasetLink).clearClasses());
                }
                %>
            <td align="center" nowrap="true"><%=innerHtml%></td>
                <%
            }
        %>
    </tr>
    <%
        }
    %>
</table>
<%
    if (cantReadOneOrMoreDatasets)
    {
%><span style="font-style: italic;">NOTE: You do not have read permission to one or more datasets. Contact the study administrator for more information.</span><%
    }
%>
