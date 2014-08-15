<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.exp.property.Domain"%>
<%@ page import="org.labkey.api.reports.model.ViewCategory"%>
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.DatasetController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetDetailsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetDisplayOrderAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetVisibilityAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DefineDatasetTypeAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageTypesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageUndefinedTypesAction" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    Study study = StudyManager.getInstance().getStudy(c);
    boolean sharedDatasets = null != StudyManager.getInstance().getSharedStudy(study);

    List<? extends DataSet> datasets = study.getDatasetsByType(DataSet.TYPE_STANDARD, DataSet.TYPE_PLACEHOLDER);
    int countUndefined = 0;
    for (DataSet def : datasets)
    {
        Domain d = def.getDomain();
        if (null == d || 0 == d.getProperties().size())
            countUndefined++;
    }
    String dateFormat = DateUtil.getDateFormatString(getContainer());
    String numberFormat = Formats.getNumberFormatString(getContainer());
%>
<table>
    <tr>
        <td>The study schedule defines the data expected for each timepoint.</td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
<%

    if (countUndefined > 0)
    {
        %><tr>
            <td>A visit map can refer to datasets that do not have defined schemas.
                <%
                    if (countUndefined == 1)
                    {
                        %>One dataset in this study does not have a defined schema.<%
                    }
                    else if (countUndefined > 1)
                    {
                        %><%= countUndefined %> datasets in this study do not have defined schemas.<%
                    }
                    else
                    {
                        %>All datasets in this study have defined schemas.<%
                    }
                %>
            </td>
            <td><%= textLink("Define Dataset Schemas", ManageUndefinedTypesAction.class)%></td>
        </tr><%
    }
    if (!datasets.isEmpty())
    {
    %><tr>
        <td>Datasets can be displayed in any order.</td>
        <td><%= textLink("Change Display Order", DatasetDisplayOrderAction.class)%></td>
    </tr><%
    }

%>
    <tr>
        <td>Dataset visibility, label, and category can all be changed.</td>
        <td><%= textLink("Change Properties", DatasetVisibilityAction.class)%></td>
    </tr>
    <tr>
        <td>Datasets may be deleted by an administrator.</td>
        <td><%= textLink("Delete Multiple Datasets", DatasetController.BulkDatasetDeleteAction.class)%></td>
    </tr>
    <tr>
        <td>Security can be configured on a per-dataset level.</td>
        <td><%= textLink("Manage Dataset Security", SecurityController.BeginAction.class)%></td>
    </tr>

    <tr>
        <td>New Datasets can be added to this study at any time.</td>
        <%
            ActionURL createURL = new ActionURL(StudyController.DefineDatasetTypeAction.class, getContainer());
            createURL.addParameter("autoDatasetId", "true");
        %>
        <td><%= textLink("Create New Dataset", createURL)%></td>
    </tr>
</table>
<% WebPartView.startTitleFrame(out, "Default Time/Date, Number Formats", null, null, null); %>
<labkey:errors/>
<%
    AdminUrls urls = urlProvider(AdminUrls.class);
    String name = c.isProject() ? "project" : "folder";
    ActionURL url = c.isProject() ? urls.getProjectSettingsURL(c) : urls.getFolderManagementSettingsURL(c);
%>
<form id="manageTypesForm" action="<%=h(buildURL(ManageTypesAction.class))%>" method="POST">
    <table>
        <tr><td>Default date format:</td><td><%=h(StringUtils.trimToEmpty(dateFormat))%></td></tr>
        <tr><td>Default number format:</td><td><%=h(StringUtils.trimToEmpty(numberFormat))%></td></tr>
        <tr><td colspan="2"><br>Default formats can be changed via the <%=textLink(name + " settings page", url)%></td></tr>
    </table>
</form>
<% WebPartView.endTitleFrame(out); %>

<% WebPartView.startTitleFrame(out, "Datasets", null, null, "datasets"); %>
<table>
    <tr>
        <th align="left">ID</th>
        <th align="left">Name</th>
        <th align="left">Label</th>
        <th align="left">Category</th>
        <th align="left">Type</th>
        <th align="left">Cohort</th>
        <th align="left">Shown</th>
        <th align="left">Demographic</th>
        <th align="left">Keys</th>
        <th align="left">Source Assay</th>
    </tr><%

    ActionURL details = new ActionURL(DatasetDetailsAction.class, c);
    for (DataSet def : datasets)
    {
        details.replaceParameter("id",String.valueOf(def.getDatasetId()));
        ViewCategory viewCategory = def.getViewCategory();
        Cohort cohort = def.getCohort();
        boolean isShared = def.isShared();
    %><tr>
        <td align=right><a href="<%=h(details)%>"><%=def.getDatasetId()%></a></td>
        <td><a href="<%=h(details)%>"><%= h(def.getName()) %><%=text(isShared?" (shared)":"")%></a></td>
        <td><% if (!def.getName().equals(def.getLabel())) {%><a href="<%=h(details)%>"><%= h(def.getLabel()) %></a><%}%>&nbsp;</td>
        <td><%=h(viewCategory != null ? viewCategory.getLabel() : null) %>&nbsp;</td>
        <td><%=h(def.getType())%>&nbsp;</td>
        <td><%=h(cohort != null ? cohort.getLabel() : "All")%></td>
        <td><%=text(def.isShowByDefault() ? "" : "hidden")%></td>
        <td><%=text(def.isDemographicData() ? "demographic" : "")%></td>
        <td><%=h(def.getKeyTypeDescription())%></td>
        <td><%=h(def.getAssayProtocol() != null ? def.getAssayProtocol().getName() : "")%></td>
    </tr><%
    }
%></table>
<%= textLink("Create New Dataset", new ActionURL(DefineDatasetTypeAction.class,c).addParameter("autoDatasetId","true"))%>
<% WebPartView.endTitleFrame(out); %>
