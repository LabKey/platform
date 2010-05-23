<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.security.SecurityPolicy"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.study.importer.StudyReload" %>
<%@ page import="org.labkey.api.study.Visit" %><%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    User user = (User)request.getUserPrincipal();
    Container c = getViewContext().getContainer();

if (null == getStudy())
{
    out.println("A study has not yet been created in this folder.<br>");
    if (c.hasPermission(user, AdminPermission.class))
    {
        ActionURL createURL = new ActionURL(StudyController.ManageStudyPropertiesAction.class, c);
        out.println(generateButton("Create Study", createURL));

        if (null != StudyReload.getPipelineRoot(c))
        {
            ActionURL importStudyURL = new ActionURL(StudyController.ImportStudyAction.class, c);
            out.println(generateButton("Import Study", importStudyURL));
        }
        else if(PipelineService.get().canModifyPipelineRoot(user, c))
        {
            ActionURL pipelineURL = urlProvider(PipelineUrls.class).urlSetup(c);
            out.println(generateButton("Pipeline Setup", pipelineURL));
        }
    }
    else
    {
%>
    Contact an administrator to create a study.
<%
    }
    return;
}
    TimepointType timepointType = getStudy().getTimepointType();
    SecurityPolicy policy = c.getPolicy();
    boolean isAdmin = policy.hasPermission(user, AdminPermission.class);
    ActionURL url = new ActionURL(StudyController.BeginAction.class, getStudy().getContainer());
    String visitLabel = StudyManager.getInstance().getVisitManager(getStudy()).getPluralLabel();
%>
<br>
<table width="100%">
    <tr><td valign="top">This study defines
<ul>
    <li><%= getDataSets().size() %> Datasets (Forms and Assays)&nbsp;<%= isAdmin ? textLink("Manage Datasets", url.setAction(StudyController.ManageTypesAction.class)) : "&nbsp;" %></li>
    <% if (timepointType != TimepointType.CONTINUOUS) { %>
    <li><%= getVisits(Visit.Order.DISPLAY).length %> <%=visitLabel%>&nbsp;<%=timepointType == TimepointType.VISIT && isAdmin && getVisits(Visit.Order.DISPLAY).length < 0 ?
                        textLink("Import Visit Map", url.setAction(StudyController.UploadVisitMapAction.class)) : "" %><%=
                        isAdmin ? textLink("Manage " + visitLabel, url.setAction(StudyController.ManageVisitsAction.class)) : "" %></li>
    <% } %>
    <li><%= getSites().length %> Labs and Sites&nbsp;<%= isAdmin ? textLink("Manage Labs/Sites", url.setAction(StudyController.ManageSitesAction.class)) : ""%></li>
<%
    if (StudyManager.getInstance().showCohorts(c, user))
    {
%>
    <li><%= getCohorts(user).length %> Cohorts&nbsp;<%= isAdmin ? textLink("Manage Cohorts", url.setAction(CohortController.ManageCohortsAction.class)) : ""%></li>
<%
    }
%>
</ul>
    </td>
        <td valign="top">
            <a href="<%=h(url.setAction(StudyController.OverviewAction.class).getLocalURIString())%>"><img src="<%=request.getContextPath()%>/_images/studyNavigator.gif" alt="Study Navigator"> </a><br>
            <%=textLink("Study Navigator", url.setAction(StudyController.OverviewAction.class))%>
        </td>
    </tr>
    </table>

<%
    if (isAdmin)
    {
        out.write(textLink("Manage Study", url.setAction(StudyController.ManageStudyAction.class)));
        out.write("&nbsp;");

        // if there is a pipeline override, show the pipeline view, else show the files webpart
        ActionURL pipelineUrl;
        if (PipelineService.get().hasSiteDefaultRoot(c))
            pipelineUrl = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline");
        else
            pipelineUrl = PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(c);

        out.write(textLink("Manage Files", pipelineUrl.toString()));
    }
    else if (policy.hasPermission(user, ManageRequestSettingsPermission.class) &&
            getStudy().getRepositorySettings().isEnableRequests())
    {
        out.write(textLink("Manage Specimen Request Settings", url.setAction(StudyController.ManageStudyAction.class)));
    }
    else if (policy.hasPermission(user, ManageRequestSettingsPermission.class))
    {
        out.write(textLink("Manage Study", url.setAction(StudyController.ManageStudyAction.class)));
    }
%>

