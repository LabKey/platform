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
<%@ page import="org.labkey.api.audit.AuditUrls"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.exp.property.Domain"%>
<%@ page import="org.labkey.api.reports.model.ViewCategory"%>
<%@ page import="org.labkey.api.study.Cohort"%>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page import="org.labkey.study.controllers.DatasetController.BulkDatasetDeleteAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetDetailsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetDisplayOrderAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DatasetVisibilityAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DefineDatasetTypeAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageUndefinedTypesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.StudyScheduleAction" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController.BeginAction" %>
<%@ page import="org.labkey.study.dataset.DatasetAuditProvider" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    Study study = StudyManager.getInstance().getStudy(c);

    List<DatasetDefinition> shadowed = StudyManager.getInstance().getShadowedDatasets(study, null);

    List<? extends Dataset> datasets = study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER);
    int countUndefined = 0;
    for (Dataset def : datasets)
    {
        Domain d = def.getDomain();
        if (null == d || 0 == d.getProperties().size())
            countUndefined++;
    }
%>
<table class="lk-fields-table">
    <tr>
        <td style="padding-right: 4px;">The study schedule defines the data expected for each timepoint.</td>
        <td><%= link("Study Schedule", StudyScheduleAction.class) %></td>
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
            <td><%= link("Define Dataset Schemas", ManageUndefinedTypesAction.class)%></td>
        </tr><%
    }
    if (!datasets.isEmpty())
    {
    %><tr>
        <td>Datasets can be displayed in any order.</td>
        <td><%= link("Change Display Order", DatasetDisplayOrderAction.class)%></td>
    </tr><%
    }

%>
    <tr>
        <td>Dataset visibility, label, and category can all be changed.</td>
        <td><%= link("Change Properties", DatasetVisibilityAction.class)%></td>
    </tr>
    <tr>
        <td>Datasets may be deleted by an administrator.</td>
        <td><%= link("Delete Multiple Datasets", BulkDatasetDeleteAction.class)%></td>
    </tr>
    <tr>
        <td>Security can be configured on a per-dataset basis.</td>
        <td><%= link("Manage Dataset Security", BeginAction.class)%></td>
    </tr>

    <tr>
        <td>New datasets can be added to this study at any time.</td>
        <%
            ActionURL createURL = new ActionURL(DefineDatasetTypeAction.class, c);
        %>
        <td><%= link("Create New Dataset", createURL)%></td>
    </tr>
    <tr>
        <td>Dataset audit logs can be viewed for all datasets in this folder.</td>
        <td><%= link("View Audit Events ", PageFlowUtil.urlProvider(AuditUrls.class).getAuditLog(getContainer(), DatasetAuditProvider.DATASET_AUDIT_EVENT, null, null))%></td>
    </tr>
</table>
<%
    FrameFactoryClassic.startTitleFrame(out, "Datasets", null, null, "datasets");
%>
<br/>
<table id="dataregion_datasets" class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">ID</td>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Label</td>
        <td class="labkey-column-header">Category</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Cohort</td>
        <td class="labkey-column-header">Shown</td>
        <td class="labkey-column-header">Demographic</td>
        <td class="labkey-column-header">Keys</td>
        <td class="labkey-column-header">Publish Source</td>
    </tr>
    <%

    int i = 0;
    ActionURL details = new ActionURL(DatasetDetailsAction.class, c);
    for (Dataset def : datasets)
    {
        details.replaceParameter("id", def.getDatasetId());
        ViewCategory viewCategory = def.getViewCategory();
        Cohort cohort = def.getCohort();
        boolean isShared = def.isShared();
    %><tr class="<%=getShadeRowClass(i++)%>">
        <td align=right><a href="<%=h(details)%>"><%=def.getDatasetId()%></a></td>
        <td><a href="<%=h(details)%>"><%= h(def.getName()) %><%=text(!isShared?"": def.getDataSharingEnum()== DatasetDefinition.DataSharing.PTID?" (shared data)":" (shared)")%></a></td>
        <td><% if (!def.getName().equals(def.getLabel())) {%><a href="<%=h(details)%>"><%= h(def.getLabel()) %></a><%}%>&nbsp;</td>
        <td><%=h(viewCategory != null ? viewCategory.getLabel() : null) %>&nbsp;</td>
        <td><%=h(def.getType())%>&nbsp;</td>
        <td><%=h(cohort != null ? cohort.getLabel() : "All")%></td>
        <td><%=text(def.isShowByDefault() ? "" : "hidden")%></td>
        <td><%=text(def.isDemographicData() ? "demographic" : "")%></td>
        <td><%=h(def.getKeyTypeDescription())%></td>
        <td><%=h(def.getPublishSource() != null ? def.getPublishSource().getLabel(def.getPublishSourceId()) : "")%></td>
    </tr><%
    }
%></table>
<br>
<%= link("Create New Dataset", new ActionURL(DefineDatasetTypeAction.class, c))%>
<% if (!shadowed.isEmpty())
{
    %><p>WARNING: One or more datasets in parent study are shadowed by datasets defined in this folder.<br><ul><%
    for (DatasetDefinition h : shadowed)
    {
        %><li><%=h.getDatasetId()%>:&nbsp;<%=h(h.getName())%></li><%
    }
    %></ul></p><%
}

    FrameFactoryClassic.endTitleFrame(out);
%>
