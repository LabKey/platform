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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.*"%>
<%@ page import="org.labkey.study.model.StudyManager"%>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ManageRequestBean> me = (JspView<SpringSpecimenController.ManageRequestBean>) HttpView.currentView();
    SpringSpecimenController.ManageRequestBean bean = me.getModelBean();
    String comments = bean.getSampleRequest().getComments();
    ViewContext context = me.getViewContext();
    if (comments == null)
        comments = "[No description provided]";
    SampleRequestActor[] actors = SampleManager.getInstance().getRequirementsProvider().getActors(context.getContainer());
    SampleRequestRequirement[] requirements = SampleManager.getInstance().getRequestRequirements(bean.getSampleRequest());
    Site destinationSite = bean.getDestinationSite();
    User creatingUser = UserManager.getUser(bean.getSampleRequest().getCreatedBy());
    Site[] sites = StudyManager.getInstance().getSites(context.getContainer());
    boolean notYetSubmitted = false;
    if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer()))
    {
        SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(context.getContainer(), context.getUser());
        notYetSubmitted = bean.getSampleRequest().getStatusId() == cartStatus.getRowId();
    }
%>
<script type="text/javascript">
    var NONSITE_ACTORS = [<%
    boolean first = true;
    for (SampleRequestActor actor : actors)
    {
        if (!actor.isPerSite())
        {
            if (first)
                first = false;
            else
                out.write(", ");
            out.write("" + actor.getRowId());
        }
    }
%>];

    function getSelectedActor()
    {
        var actorSelect = document.addRequirementForm.newActor;
        if (actorSelect.selectedIndex >= 0)
            return parseInt(actorSelect.options[actorSelect.selectedIndex].value);
        else
            return -1;
    }

    function getSelectedSite()
    {
        var siteSelect = document.addRequirementForm.newSite;
        if (siteSelect.selectedIndex >= 0)
            return parseInt(siteSelect.options[siteSelect.selectedIndex].value);
        else
            return -1;
    }

    function isNonSiteActorSelected()
    {
        var selectedActor = getSelectedActor();
        for (var i = 0; i < NONSITE_ACTORS.length; i++)
        {
            if (NONSITE_ACTORS[i] == selectedActor)
                return true;
        }
        return false;
    }

    function validateNewRequirement()
    {
        var selectedSite = getSelectedSite();
        var selectedActor = getSelectedActor();

        if (selectedActor <= 0)
        {
            alert("An actor is required for all new requirements.");
            return false;
        }

        var description = document.addRequirementForm.newDescription.value;
        if (!description)
        {
            alert("A description is required for all new requirements.");
            return false;
        }
        var isNonSiteActor = isNonSiteActorSelected();

        if (isNonSiteActor && selectedSite > 0)
        {
            alert("A non-location-specific actor cannot have a location selected.");
            return false;
        }

        if (!isNonSiteActor && selectedSite <= 0)
        {
            alert("The selected actor is location-affiliated: please select a location.");
            return false;
        }
        return true;
    }

    function updateSiteSelector()
    {
        var siteSelect = document.addRequirementForm.newSite;
        if (isNonSiteActorSelected())
        {
            siteSelect.selectedIndex = -1;
            siteSelect.disabled = true;
        }
        else
        {
            siteSelect.disabled = false;
        }
    }
</script>
<%= PageFlowUtil.getStrutsError(request, "main") %>
<%
    if (bean.isSuccessfulSubmission())
    {
%>
<h3>Your request has been successfully submitted.</h3>
<%
    }
%>
    <table class="labkey-request-warnings">
<%
    boolean multipleSites = bean.getProvidingSites().length > 1;
    if (bean.hasMissingSpecimens() || multipleSites)
    {
%>
        <tr class="labkey-wp-header">
            <th align="left">Request  Warnings</th>
        </tr>
<%
        if (bean.hasMissingSpecimens())
        {
%>
        <tr>
            <td class="labkey-form-label">
                <span class="labkey-error"><b>WARNING: Missing Specimens</b></span><br><br>
                The following specimen(s) are part of this request, but have been deleted from the database:<br>
                <%
                    for (String specId : bean.getMissingSpecimens())
                    {
                        %><b><%= specId %></b><br><%
                    }

                    if (bean.isRequestManager())
                    {
                %>
                <br>You may remove these specimens from this request if they are not expected to be re-added to the database.<br>
                <%= generateButton("Delete missing specimens", "deleteMissingRequestSpecimens.view?id=" +
                        bean.getSampleRequest().getRowId(), "return confirm('Delete missing specimens?  This action cannot be undone.')")%>
                <%
                    }
                %>
            </td>
        </tr>
<%
        }
        if (multipleSites)
        {
%>
        <tr>
            <td class="labkey-form-label">
                <span class="labkey-error"><b>WARNING: Multiple providing locations.</b></span><br>
                Requests containing vials from multiple providing locations may require increased processing time.
                Providing locations for this request are:<br>
                <%
                    for (Site site : bean.getProvidingSites())
                    {
                        %><b><%= h(site.getLabel()) %></b><br><%
                    }
                %>
            </td>
        </tr>
<%
        }
    }
    if (bean.isRequestManager() && (bean.isFinalState() || bean.isRequirementsComplete()))
    {
%>
        <tr class="labkey-wp-header">
            <th align="left">Request  Notes</th>
        </tr>
        <tr>
            <td class="labkey-form-label">
<%
        if (bean.isFinalState())
        {
%>
                This request is in a final state; no changes are allowed.<br>
                To make changes, you must <a href="manageRequestStatus.view?id=<%= bean.getSampleRequest().getRowId() %>">
                change the request's status</a> to a non-final state.
<%
        }
        else if (bean.isRequirementsComplete())
        {
%>
                This request's requirements are complete. Next steps include:<br>
                <ul>
                    <li>Email specimen lists to their originating locations: <%= textLink("Originating Location Specimen Lists",
                        "labSpecimenLists.view?id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpringSpecimenController.LabSpecimenListsBean.Type.ORIGINATING.toString()) %></li>
                    <li>Email specimen lists to their providing locations: <%= textLink("Providing Location Specimen Lists",
                        "labSpecimenLists.view?id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpringSpecimenController.LabSpecimenListsBean.Type.PROVIDING.toString()) %></li>
                    <li>Update request status to indicate completion: <%= textLink("Update Status", "manageRequestStatus.view?id=" + bean.getSampleRequest().getRowId()) %></li>
                </ul>
<%
        }
%>
            </td>
        </tr>
<%
    }
    if (notYetSubmitted)
    {
%>
        <tr class="labkey-wp-header">
            <th align="left">Unsubmitted Request</th>
        </tr>
        <tr>
            <td class="labkey-form-label"><span class="labkey-error"><b>This request has not been submitted.</b></span>
<%
        if (SampleManager.getInstance().hasEditRequestPermissions(context.getUser(), bean.getSampleRequest()))
        {
            Specimen[] specimens = bean.getSampleRequest().getSpecimens();
            if (specimens != null && specimens.length > 0)
            {
%>
                Request processing will begin after the request has been submitted.<br><br>
                <%= generateButton("Submit Request", "submitRequest.view?id=" + bean.getSampleRequest().getRowId(),
                        "return confirm('" + SpringSpecimenController.ManageRequestBean.SUBMISSION_WARNING + "')")%>
<%
            }
            else
            {
%>
                You must add specimens before submitting your request.<br><br>
                <%= SampleManager.getInstance().hasEditRequestPermissions(context.getUser(), bean.getSampleRequest()) ?
                        generateButton("Specimen Search", "showSearch.view?showVials=true") : "" %>
<%
            }
%>
                <%= generateButton("Cancel Request", "deleteRequest.view?id=" + bean.getSampleRequest().getRowId(),
                        "return confirm('" + SpringSpecimenController.ManageRequestBean.CANCELLATION_WARNING + "')")%>
            </td>
        </tr>
<%
        }
        else
        {
%>
                Only the request creator (<%= creatingUser.getDisplayName(context) %>) or an administrator may submit or cancel this request.
<%
        }
    }
%>
        <tr class="labkey-wp-header">
            <th align="left">Request Information</th>
        </tr>
        <tr>
            <td>
                <table>
                    <tr>
                        <th valign="top" align="right">Requester</th>
                        <td><%= creatingUser != null ? h(creatingUser.getDisplayName(context)) : "Unknown" %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Requesting Location</th>
                        <td><%= destinationSite != null ? h(destinationSite.getDisplayName()) : "Not specified" %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Request Date</th>
                        <td><%= h(formatDateTime(bean.getSampleRequest().getCreated())) %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Description</th>
                        <td><%= h(comments).replaceAll("\\n", "<br>\n") %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Status</th>
                        <td><%= bean.getStatus().getLabel() %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">&nbsp;</th>
                        <td>
                            <%= textLink("View History", "requestHistory.view?id=" + bean.getSampleRequest().getRowId()) %>&nbsp;
                            <%= bean.isRequestManager() ? textLink("Update Status", "manageRequestStatus.view?id=" + bean.getSampleRequest().getRowId()) : "" %>
                            <%= bean.isRequestManager() ? textLink("Originating Location Specimen Lists",
                                    "labSpecimenLists.view?id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpringSpecimenController.LabSpecimenListsBean.Type.ORIGINATING.toString()) : "" %>
                            <%= bean.isRequestManager() ? textLink("Providing Location Specimen Lists",
                                    "labSpecimenLists.view?id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpringSpecimenController.LabSpecimenListsBean.Type.PROVIDING.toString()) : "" %>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <form action="manageRequest.post" name="addRequirementForm" enctype="multipart/form-data" method="POST">
        <input type="hidden" name="id" value="<%= bean.getSampleRequest().getRowId()%>">
        <tr class="labkey-wp-header">
            <th align="left">Current Requirements</th>
        </tr>
        <tr>
            <td>
                <%
                    if (!bean.isRequestManager() && requirements.length == 0)
                    {
                %>
                    No requirements are associated with this request.
                <%
                    }
                    else
                    {
                %>
                    <table class="labkey-data-region">
                        <tr>
                            <th align=left>Actor</th>
                            <th align=left>Location</th>
                            <th align=left>Description</th>
                            <th align=left><%= requirements.length > 0 ? "Status" : "" %></th>
                            <th align=left>&nbsp;</th>
                        </tr>
                    <%
                        for (SampleRequestRequirement requirement : requirements)
                        {
                            Site site = requirement.getSite();
                            String siteLabel;
                            if (site == null)
                                siteLabel = "N/A";
                            else if (site.getLabel() == null)
                                siteLabel = "";
                            else
                                siteLabel = site.getDisplayName();
                    %>
                            <tr>
                                <td><%= h(requirement.getActor().getLabel()) %></td>
                                <td><%= h(siteLabel) %></td>
                                <td><%= requirement.getDescription() != null ? h(requirement.getDescription()) : "&nbsp;" %></td>
                                <td>
                                    <span class="<%= requirement.isComplete() ? "labkey-message" : "labkey-error"%>" style="font-weight:bold;">
                                        <%= requirement.isComplete() ? "Complete" : "Incomplete" %>
                                    </span>
                                </td>
                                <td>
                                    <%= textLink("Details", "manageRequirement.view?id=" + requirement.getRequestId() + "&requirementId=" + requirement.getRowId())%>
                                </td>
                            </tr>
                    <%
                        }
                        if (bean.isRequestManager() && !bean.isFinalState())
                        {
                    %>
                        <tr>
                            <td>
                                <select name="newActor" onChange="updateSiteSelector()">
                                    <option value="-1"></option>
                                    <%
                                        for (SampleRequestActor actor : actors)
                                        {
                                    %>
                                    <option value="<%= actor.getRowId() %>"><%= h(actor.getLabel()) %></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </td>
                            <td>
                                <select name="newSite">
                                    <option value="-1"></option>
                                    <option value="0">[N/A]</option>
                                    <%
                                        for (Site site : sites)
                                        {
                                    %>
                                    <option value="<%= site.getRowId() %>"><%= h(site.getDisplayName()) %></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </td>
                            <td colspan="2"><input type="text" name="newDescription" size="50"></td>
                            <td><%= buttonImg("Add Requirement", "if(validateNewRequirement()) document.addRequirementForm.submit(); return false;")%></td>
                        </tr>
                        <%
                            }
                        %>
                </table>
                <%
                    }
                %>
            </td>
        </tr>
        </form>
        <tr class="labkey-wp-header">
            <th align="left">Associated Specimens</th>
        </tr>
        <tr>
            <td>
                <%
                    if (bean.getSpecimenQueryView() != null)
                    {
                        me.include(bean.getSpecimenQueryView(), out);
                    }
                    else
                    {
                %>
                No specimens are associated with this request.<br>
                <%= SampleManager.getInstance().hasEditRequestPermissions(context.getUser(), bean.getSampleRequest()) ? 
                        generateButton("Specimen Search", "showSearch.view?showVials=true") : "" %>
                <%
                    }
                %>
            </td>
        </tr>
    </table>
