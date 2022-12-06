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
<%@ page import="org.labkey.api.specimen.location.LocationImpl" %>
<%@ page import="org.labkey.api.specimen.location.LocationManager" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.specimen.actions.ShowGroupMembersAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.DeleteActorAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageActorOrderAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageActorsAction" %>
<%@ page import="org.labkey.specimen.model.SpecimenRequestActor" %>
<%@ page import="org.labkey.specimen.requirements.SpecimenRequestRequirementProvider" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Study> me = (JspView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
    SpecimenRequestActor[] actors = SpecimenRequestRequirementProvider.get().getActors(getContainer());
    String showMemberSitesIdStr = request.getParameter("showMemberSites");
    int showMemberSitesId = -1;
    Set<Integer> inUseActorIds = SpecimenRequestRequirementProvider.get().getActorsInUseSet(getContainer());
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
<labkey:errors/>
<labkey:form action="<%=urlFor(ManageActorsAction.class)%>" name="manageActors" method="POST">
    <table class="lk-fields-table">
        <tr>
            <td>&nbsp;</td>
            <td style="font-weight: bold;">Actor Name</td>
            <td style="font-weight: bold;">Actor Type</td>
            <td>&nbsp;</td>
        </tr>
        <%
        if (actors != null && actors.length > 0)
        {
            for (SpecimenRequestActor actor : actors)
            {
                ActionURL updateMembersLink = null;
                if (actor.isPerSite())
                {
                    if (showMemberSitesId != actor.getRowId())
                        updateMembersLink = urlFor(ManageActorsAction.class).addParameter("showMemberSites", actor.getRowId());
                }
                else
                    updateMembersLink = ShowGroupMembersAction.getShowGroupMembersURL(getContainer(), actor.getRowId(), null, null);
        %>
        <tr>
            <td align="center">&nbsp;</td>
            <td valign="top">
                <input type="hidden" name="ids" value="<%= actor.getRowId() %>">
                <input type="text" name="labels" size="40"
                       value="<%=h(actor.getLabel())%>">
            </td>
            <td valign="top"><%=h(actor.isPerSite() ? "Multiple Per Study (Location Affiliated)" : "One Per Study")%></td>
            <td>
                <%
                    if (showMemberSitesId == actor.getRowId())
                    {
                        if (study.getLocations().size() > 0)
                        {
                    %>
                        <b>Choose Site</b>:<br>
                        <%
                            for (LocationImpl location : LocationManager.get().getLocations(study.getContainer()))
                            {
                            %><a href="<%=h(ShowGroupMembersAction.getShowGroupMembersURL(getContainer(), actor.getRowId(), location.getRowId(), null))%>"><%= h(location.getDisplayName()) %></a><br><%
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
                        %><%= link("Update Members").href(updateMembersLink) %><%
                        if (!inUseActorIds.contains(actor.getRowId()))
                        {
                            %><%=link("Delete").onClick("return LABKEY.Utils.confirmAndPost('Deleting this actor will delete all information about its membership. All member emails will need to be entered again if you recreate this actor.', '" + h(urlFor(DeleteActorAction.class).addParameter("id", actor.getRowId())) + "')") %><%
                        }
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
                <%= button("Save").submit(true) %>&nbsp;
                <%= button("Done").submit(true).onClick("document.manageActors.nextPage.value=''; return true;") %>
                <%= button("Cancel").href(urlProvider(StudyUrls.class).getManageStudyURL(study.getContainer())) %>&nbsp;
                <%= button("Change Order").submit(true).onClick("document.manageActors.nextPage.value=" + q(urlFor(ManageActorOrderAction.class)) + "; return true;") %>
                <input type="hidden" name="nextPage" value="<%=h(new ActionURL(ManageActorsAction.class, study.getContainer()))%>">
            </td>
            <td>&nbsp;</td>
        </tr>
    </table>
</labkey:form>