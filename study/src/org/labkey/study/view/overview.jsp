<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.labkey.study.model.*"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.model.QCStateSet" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.study.CohortFilter" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyController.OverviewBean> me = (JspView<StudyController.OverviewBean>) HttpView.currentView();
    StudyController.OverviewBean bean = me.getModelBean();
    String contextPath = request.getContextPath();
    Study study = bean.study;
    Container container = study.getContainer();
    User user = (User) request.getUserPrincipal();
    StudyManager manager = StudyManager.getInstance();

    boolean showCohorts = manager.showCohorts(container, user);
    CohortImpl selectedCohort = null;
    CohortImpl[] cohorts = null;
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

    VisitImpl[] visits = manager.getVisits(study, selectedCohort, user, Visit.Order.DISPLAY);
    DataSetDefinition[] datasets = manager.getDataSetDefinitions(study, selectedCohort);
    boolean cantReadOneOrMoreDatasets = false;
    String basePage = "overview.view?";
    if (selectedCohort != null)
        basePage += "cohortId=" + selectedCohort.getRowId() + "&";
    if (selectedQCStateSet != null)
        basePage += "QCState=" + selectedQCStateSet.getFormValue() + "&";

%><%= bean.canManage ? textLink("Manage Study", "manageStudy.view") : ""%>
&nbsp;<%= textLink("Views", new ActionURL(ReportsController.BeginAction.class, container))%>&nbsp;
&nbsp;<%= textLink("Specimens", new ActionURL("Study-Samples", "begin", container))%>&nbsp;
<%
    boolean hasHiddenData = false;
    for (int i = 0; i < visits.length && !hasHiddenData; i++)
        hasHiddenData = !visits[i].isShowByDefault();
    for (int i = 0; i < datasets.length && !hasHiddenData; i++)
        hasHiddenData = !datasets[i].isShowByDefault();
    if (hasHiddenData)
    {
        String viewLink = bean.showAll ? textLink("Hide Extra Data", basePage) :
                textLink("Show Hidden Data", basePage + "showAll=1");
        out.write(viewLink);
    }


%>
<form action="overview.view" name="changeFilterForm" method="GET">
    <input type="hidden" name="showAll" value="<%= bean.showAll ? "1" : "0" %>">
<%
    if (showCohorts)
    {
%>
<br><br>
    <input type="hidden" name="<%= CohortFilter.Params.cohortFilterType.name() %>" value="<%= CohortFilter.Type.PTID_CURRENT.name() %>">
    <%= StudyService.get().getSubjectNounSingular(container) %>'s current cohort: <select name="<%= CohortFilter.Params.cohortId.name() %>" onchange="document.changeFilterForm.submit()">
    <option value="">All</option>
    <%
        for (CohortImpl cohort : cohorts)
        {
    %>
    <option value="<%= cohort.getRowId() %>" <%= selectedCohort != null && cohort.getRowId() == selectedCohort.getRowId() ? "SELECTED" : "" %>>
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
    QC State: <select name="<%= BaseStudyController.SharedFormParameters.QCState.name() %>" onchange="document.changeFilterForm.submit()">
        <%
            for (QCStateSet set : qcStateSetOptions)
            {
        %>
        <option value="<%= set.getFormValue() %>" <%= set.equals(selectedQCStateSet) ? "SELECTED" : "" %>>
            <%= h(set.getLabel()) %>
        </option>
        <%
            }
        %>
    </select>
    <%
    }
    %>
</form>
<br><br>
<table class="labkey-data-region labkey-show-borders"><colgroup><col><col>
    <%

        for (VisitImpl visit : visits)
        {
            if (!bean.showAll && !visit.isShowByDefault())
                continue;
            %><col><%
        }
        %></colgroup>
    <tr class="labkey-alternate-row labkey-col-header">
        <th class="labkey-data-region-title"><img alt="" width=60 height=1 src="<%=contextPath%>/_.gif"></th>
        <th style="font-weight:bold;">ALL</th><%

        for (VisitImpl visit : visits)
        {
            if (!bean.showAll && !visit.isShowByDefault())
                continue;
            String label = visit.getDisplayString();
            %><th align="center" valign="top"><%= h(label) %></th><%
            //visitSummary.view?id=411.0
        }
        %>
    </tr>
    <%
    int row = 0;
    VisitMapKey key = new VisitMapKey(0,0);
    String prevCategory = null;
    boolean useCategories = false;
    for (DataSetDefinition dataSet : datasets)
    {
        if (dataSet.getCategory() != null)
        {
            useCategories = true;
            break;
        }
    }
    Map<VisitMapKey,Boolean> requiredMap = StudyManager.getInstance().getRequiredMap(study);

    for (DataSetDefinition dataSet : datasets)
    {
        if (!bean.showAll && !dataSet.isShowByDefault())
            continue;

        boolean userCanRead = dataSet.canRead(user);
        if (!userCanRead)
            cantReadOneOrMoreDatasets = true;

        row++;
        key.datasetId = dataSet.getDataSetId();
        if (useCategories)
        {
            String category = dataSet.getCategory();
            if (category == null)
                category = "Uncategorized";
            if (!category.equals(prevCategory))
            {
                %><tr><td class="labkey-highlight-cell" align="left" colspan="<%= visits.length + 2%>"><%= h(category) %></td></tr><%
            }
            prevCategory = category;
        }

        String dataSetLabel = (dataSet.getLabel() != null ? dataSet.getLabel() : "" + dataSet.getDataSetId());
        String className= row%2==0 ? "labkey-alternate-row" : "labkey-row";
        %>
        <tr class="<%= className %>" ><td align="center" class="labkey-row-header"><%= h(dataSetLabel) %><%
        if (null != StringUtils.trimToNull(dataSet.getDescription()))
        {
            %><%=PageFlowUtil.helpPopup(dataSetLabel, dataSet.getDescription())%><%
        }
        %></td>
        <td style="font-weight:bold;"><%
        int totalCount = 0;
        for (VisitImpl visit : visits)
        {
            key.visitRowId = visit.getRowId();
            Integer c = bean.visitMapSummary.get(key);
            totalCount += c == null ? 0 : c;
        }
        if (userCanRead)
        {
            ActionURL defaultReportURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, container);
            defaultReportURL.addParameter(DataSetDefinition.DATASETKEY, dataSet.getDataSetId());
            if (selectedCohort != null && bean.cohortFilter != null)
                bean.cohortFilter.addURLParameters(defaultReportURL);
            if (bean.qcStates != null)
                defaultReportURL.addParameter("QCState", bean.qcStates.getFormValue());

            %><a href="<%= defaultReportURL.getLocalURIString() %>"><%=totalCount%></a><%
        }
        else
        {
            %><%=totalCount%><%
        }
        %></td><%

        for (VisitImpl visit : visits)
        {
            if (!bean.showAll && !visit.isShowByDefault())
                continue;

            key.visitRowId = visit.getRowId();
            Integer c = bean.visitMapSummary.get(key);
            int count = c == null ? 0 : c;

            String innerHtml;
            Boolean b = requiredMap.get(key);
            boolean isRequired = b == Boolean.TRUE;
            boolean isOptional = b == Boolean.FALSE;

            if (isRequired)
            {
                innerHtml = ""  + count;
            }
            else if (isOptional)
            {
                innerHtml = ""  + count;
            }
            else
            {
                innerHtml = count == 0 ? "&nbsp;" : "" + count;
            }

            %><td align="center"><%

            if ((isRequired || isOptional || count > 0) && userCanRead)
            {
                ActionURL datasetLink = new ActionURL(StudyController.DatasetAction.class, container);
                datasetLink.addParameter(VisitImpl.VISITKEY, visit.getRowId());
                datasetLink.addParameter(DataSetDefinition.DATASETKEY, dataSet.getDataSetId());
                if (selectedCohort != null)
                    bean.cohortFilter.addURLParameters(datasetLink);
                if (bean.qcStates != null)
                    datasetLink.addParameter(BaseStudyController.SharedFormParameters.QCState, bean.qcStates.getFormValue());

                %><a href="<%= datasetLink.getLocalURIString() %>"><%= innerHtml %></a><%
            }
            else
            {
                %><%= innerHtml %><%
            }
            %></td><%
        }
        %></tr>
    <%
    }
    %>
</table>
<%
    if (cantReadOneOrMoreDatasets)
    {
        %><i>NOTE: user does not have read permission on one or more datasets.  Contact the study administrator for more information.<%
    }
%>
