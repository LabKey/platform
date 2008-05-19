<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    User user = (User)request.getUserPrincipal();
if (null == getStudy())
{
    out.println("A study has not yet been created in this folder.<br>");
    if (getViewContext().hasPermission(ACL.PERM_ADMIN))
    {
        ActionURL createURL = new ActionURL("Study", "manageStudyProperties.view", getViewContext().getContainer());
        out.println(buttonLink("Create Study", createURL));
    }
    else
    {
%>
    Contact an administrator to create a study.
<%
    }
    return;
}
    boolean dateBased = getStudy().isDateBased();
    boolean isAdmin = getStudy().getContainer().hasPermission(user, ACL.PERM_ADMIN);
    ActionURL url = new ActionURL("Study", "begin", getStudy().getContainer());
    String visitLabel = StudyManager.getInstance().getVisitManager(getStudy()).getPluralLabel();
%>
<br>
<table class="normal" width="100%">
    <tr><td valign="top">This study defines
<ul>
    <li><%= getDataSets().length %> Datasets (Forms and Assays) &nbsp;<%= isAdmin ? textLink("Manage Datasets", url.setAction("manageTypes.view")) : "&nbsp;" %></li>
    <li><%= getVisits().length %> <%=visitLabel%>&nbsp;<%=!dateBased && isAdmin && getVisits().length < 0 ?
                        textLink("Import Visit Map", url.setAction("uploadVisitMap.view")) : "" %><%=
                        isAdmin ? textLink("Manage " + visitLabel, url.setAction("manageVisits.view")) : "" %></li>
    <li><%= getSites().length %> Labs and Sites&nbsp;<%= isAdmin ? textLink("Manage Labs/Sites", url.setAction("manageSites.view")) : ""%></li>
<%
    if (StudyManager.getInstance().showCohorts(getViewContext().getContainer(), getViewContext().getUser()))
    {
%>
    <li><%= getCohorts(getViewContext().getUser()).length %> Cohorts&nbsp;<%= isAdmin ? textLink("Manage Cohorts", url.setAction("manageCohorts.view")) : ""%></li>
<%
    }
%>
</ul>
    </td>
        <td valign="top">
            <a href="<%=h(url.setAction("overview.view").getLocalURIString())%>"><img src="<%=request.getContextPath()%>/_images/studyNavigator.gif" alt="Study Navigator" border=0> </a><br>
            <%=textLink("Study Navigator", url.setAction("overview.view"))%>
        </td>
    </tr>
    </table>

<%
    if (isAdmin)
    {
        out.write(textLink("Manage Study", url.setAction("manageStudy.view")));
        out.write("&nbsp;");
        out.write(textLink("Data Pipeline", url.setPageFlow("Pipeline").setAction("begin.view")));
    }
%>

