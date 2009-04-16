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
<%@ page import="org.labkey.study.model.SampleRequestActor" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.Site"%>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Study> me = (JspView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
    SampleRequestActor[] actors = study.getSampleRequestActors();
    String showMemberSitesIdStr = request.getParameter("showMemberSites");
    int showMemberSitesId = -1;
    Set<Integer> inUseActorIds = study.getSampleRequestActorsInUse();
    if (showMemberSitesIdStr != null && showMemberSitesIdStr.length() > 0)
    {
        try
        {
            showMemberSitesId = Integer.parseInt(showMemberSitesIdStr);
        }
        catch (NumberFormatException e)
        {
            // fall through
        }
    }
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>
<form action="manageActors.post" name="manageActors" method="POST">
    <table>
        <tr>
            <th>&nbsp;</th>
            <th>Actor Name</th>
            <th>Actor Type</th>
            <th>&nbsp;</th>
        </tr>
        <%
        if (actors != null && actors.length > 0)
        {
            for (SampleRequestActor actor : actors)
            {
                String updateMembersLink = null;
                if (actor.isPerSite())
                {
                    if (showMemberSitesId != actor.getRowId())
                        updateMembersLink = "manageActors.view?showMemberSites=" + + actor.getRowId();
                }
                else
                    updateMembersLink = "showGroupMembers.view?id=" + actor.getRowId();
        %>
        <tr>
            <td align="center">&nbsp;</td>
            <td valign="top">
                <input type="hidden" name="ids" value="<%= actor.getRowId() %>">
                <input type="text" name="labels" size="40"
                       value="<%= actor.getLabel() != null ? h(actor.getLabel()) : "" %>">
            </td>
            <td valign="top"><%= actor.isPerSite() ? "Multiple Per Study (Location Affiliated)" : "One Per Study" %></td>
            <td>
                <%
                    if (showMemberSitesId == actor.getRowId())
                    {
                        if (study.getSites().length > 0)
                        {
                    %>
                        <b>Choose Site</b>:<br>
                        <%
                            for (Site site : study.getSites())
                            {
                            %><a href="<%= "showGroupMembers.view?id=" + actor.getRowId() + "&siteId=" + site.getRowId() %>"><%= site.getDisplayName() %></a><br><%
                            }
                        }
                        else
                        {
                        %>
                <b>No Sites Configured</b>
                <%
                        }
                    }
                    else
                    {
                %>
                <%= textLink("Update Members", updateMembersLink) %>
                <%=  inUseActorIds.contains(actor.getRowId()) ? "" :
                        textLink("Delete", "deleteActor.view?id=" + actor.getRowId(), "return confirm('Deleting this actor will delete all information about its membership.  All member emails will need to be entered again if you recreate this actor.')", null) %>
                <%
                    }
                %>
            </td>
        </tr>
        <%
                }
            }
        %>
        <tr>
            <th>New Actor:</th>
            <td><input type="text" name="newLabel" size="40"></td>
            <td>
                <select name="newPerSite">
                    <option value="false"></option>
                    <option value="false">One Per Study</option>
                    <option value="true">Multiple Per Study (Location Affiliated)</option>
                </select>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= generateSubmitButton("Save")%>&nbsp;
                <%= buttonImg("Done", "document.manageActors.nextPage.value=''; return true;")%>
                <%= generateButton("Cancel", new ActionURL(StudyController.ManageStudyAction.class, study.getContainer()))%>&nbsp;
                <%= buttonImg("Change Order", "document.manageActors.nextPage.value='manageActorOrder'; return true;")%>
                <input type="hidden" name="nextPage" value="manageActors">
            </td>
            <td>&nbsp;</td>
        </tr>
    </table>
</form>