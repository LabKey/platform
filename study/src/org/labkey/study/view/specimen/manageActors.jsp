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
<%@ page import="org.labkey.study.model.SpecimenRequestActor" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.LocationImpl"%>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.controllers.specimen.ShowGroupMembersAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyImpl> me = (JspView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();
    SpecimenRequestActor[] actors = study.getSampleRequestActors();
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
<labkey:errors/>
<labkey:form action="<%=h(buildURL(SpecimenController.ManageActorsAction.class))%>" name="manageActors" method="POST">
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
                String updateMembersLink = null;
                if (actor.isPerSite())
                {
                    if (showMemberSitesId != actor.getRowId())
                        updateMembersLink =  buildURL(SpecimenController.ManageActorsAction.class) + "showMemberSites=" + actor.getRowId();
                }
                else
                    updateMembersLink = buildURL(ShowGroupMembersAction.class) + "id=" + actor.getRowId();
        %>
        <tr>
            <td align="center">&nbsp;</td>
            <td valign="top">
                <input type="hidden" name="ids" value="<%= actor.getRowId() %>">
                <input type="text" name="labels" size="40"
                       value="<%= h(actor.getLabel()) %>">
            </td>
            <td valign="top"><%= text(actor.isPerSite() ? "Multiple Per Study (Location Affiliated)" : "One Per Study") %></td>
            <td>
                <%
                    if (showMemberSitesId == actor.getRowId())
                    {
                        if (study.getLocations().size() > 0)
                        {
                    %>
                        <b>Choose Site</b>:<br>
                        <%
                            for (LocationImpl location : study.getLocations())
                            {
                            %><a href="<%= h(buildURL(ShowGroupMembersAction.class)) + "id=" + actor.getRowId() + "&locationId=" + location.getRowId() %>"><%= h(location.getDisplayName()) %></a><br><%
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
                        %><%= textLink("Update Members", updateMembersLink) %><%
                        if (!inUseActorIds.contains(actor.getRowId()))
                        {
                            %><%=textLink("Delete", buildURL(SpecimenController.DeleteActorAction.class) + "id=" + actor.getRowId(), "return confirm('Deleting this actor will delete all information about its membership.  All member emails will need to be entered again if you recreate this actor.')", null) %><%
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
                <%= button("Cancel").href(new ActionURL(StudyController.ManageStudyAction.class, study.getContainer())) %>&nbsp;
                <%= button("Change Order").submit(true).onClick("document.manageActors.nextPage.value=" + q(new ActionURL(SpecimenController.ManageActorOrderAction.class, study.getContainer()).getLocalURIString()) + "; return true;") %>
                <input type="hidden" name="nextPage" value="<%=new ActionURL(SpecimenController.ManageActorsAction.class, study.getContainer()) %>">
            </td>
            <td>&nbsp;</td>
        </tr>
    </table>
</labkey:form>