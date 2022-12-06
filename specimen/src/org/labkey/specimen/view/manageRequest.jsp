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
<%@ page import="org.labkey.api.collections.LabKeyCollectors"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.UserManager"%>
<%@ page import="org.labkey.api.specimen.SpecimenRequestStatus"%>
<%@ page import="org.labkey.api.specimen.Vial" %>
<%@ page import="org.labkey.api.specimen.location.LocationImpl"%>
<%@ page import="org.labkey.api.specimen.location.LocationManager" %>
<%@ page import="org.labkey.api.specimen.settings.SettingsManager" %>
<%@ page import="org.labkey.api.study.Location" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.StudyUtils" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.SafeToRender" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.specimen.SpecimenManager" %>
<%@ page import="org.labkey.specimen.SpecimenRequestManager" %>
<%@ page import="org.labkey.specimen.actions.ManageRequestBean" %>
<%@ page import="org.labkey.specimen.actions.ShowSearchAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.DeleteMissingRequestSpecimensAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.DeleteRequestAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ExtendedSpecimenRequestAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ImportVialIdsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.LabSpecimenListsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.LabSpecimenListsBean" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageRequestAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageRequirementAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.RequestHistoryAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.SubmitRequestAction" %>
<%@ page import="org.labkey.specimen.model.SpecimenRequestActor" %>
<%@ page import="org.labkey.specimen.requirements.SpecimenRequestRequirement" %>
<%@ page import="org.labkey.specimen.requirements.SpecimenRequestRequirementProvider" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("FileUploadField.js");
    }
%>
<%
    JspView<ManageRequestBean> me = (JspView<ManageRequestBean>) HttpView.currentView();
    Container c = getContainer();
    User user = getUser();
    ManageRequestBean bean = me.getModelBean();
    String comments = bean.getSpecimenRequest().getComments();
    if (comments == null)
        comments = "[No description provided]";
    boolean hasExtendedRequestView = SpecimenManager.get().getExtendedSpecimenRequestView(getViewContext()) != null;
    SpecimenRequestActor[] actors = SpecimenRequestRequirementProvider.get().getActors(c);
    SpecimenRequestRequirement[] requirements = SpecimenRequestManager.get().getRequestRequirements(bean.getSpecimenRequest());
    Location destinationLocation = bean.getDestinationSite();
    User creatingUser = UserManager.getUser(bean.getSpecimenRequest().getCreatedBy());
    List<LocationImpl> locations = LocationManager.get().getLocations(c);
    boolean notYetSubmitted = false;
    if (SettingsManager.get().isSpecimenShoppingCartEnabled(c))
    {
        SpecimenRequestStatus cartStatus = SpecimenRequestManager.get().getRequestShoppingCartStatus(c, user);
        notYetSubmitted = bean.getSpecimenRequest().getStatusId() == cartStatus.getRowId();
    }

    SafeToRender specimenSearchButton = SpecimenRequestManager.get().hasEditRequestPermissions(user, bean.getSpecimenRequest()) ?
        button("Specimen Search").href(ShowSearchAction.getShowSearchURL(getContainer(), true)) : HtmlString.EMPTY_STRING;
    SafeToRender importVialIdsButton = SpecimenRequestManager.get().hasEditRequestPermissions(user, bean.getSpecimenRequest()) ?
        button("Upload Specimen Ids").href(urlFor(ImportVialIdsAction.class).addParameter("id", bean.getSpecimenRequest().getRowId())) : HtmlString.EMPTY_STRING;

    String availableStudyName = ContainerManager.getAvailableChildContainerName(c, "New Study");
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    var NONSITE_ACTORS = <%=Arrays.stream(actors)
        .filter(actor->!actor.isPerSite())
        .map(SpecimenRequestActor::getRowId)
        .collect(LabKeyCollectors.toJSONArray())%>;

    setCookieToRequestId(<%= bean.getSpecimenRequest().getRowId()%>);


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

    function setCookieToRequestId(requestId)
    {
        LABKEY.Utils.setCookie("selectedRequest", requestId, true);
    }

</script>
<labkey:errors/>
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
    if (!SpecimenService.get().getRequestCustomizer().hideRequestWarnings()) { %>
    <%
        boolean multipleSites = bean.getProvidingLocations().length > 1;
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
                            %><b><%= h(specId) %></b><br><%
                        }

                        if (bean.isRequestManager())
                        {
                    %>
                    <br>You may remove these specimens from this request if they are not expected to be re-added to the database.<br>
                    <%= button("Delete missing specimens")
                            .onClick("return LABKEY.Utils.confirmAndPost('Delete missing specimens? This action cannot be undone.', '" + h(urlFor(DeleteMissingRequestSpecimensAction.class).addParameter("id", bean.getSpecimenRequest().getRowId())) + "')") %><%
                        }
                    %>
                </td>
            </tr>
    <%
            }

            if (multipleSites && !bean.isFinalState())
            {
    %>
            <tr>
                <td class="labkey-form-label">
                    <span class="labkey-error"><b>WARNING: Vials are at multiple locations.</b></span><br>
                    Requests containing vials from multiple providing locations may require increased processing time.<br>
                    Multiple locations are expected if some vials have already shipped while others have not.
                    Current locations for this request are:<br>
                    <%
                        for (Location location : bean.getProvidingLocations())
                        {
                            %><b><%= h(location.getLabel()) %></b><br><%
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
                    To make changes, you must <a href="<%=h(SpecimenController.getManageRequestStatusURL(getContainer(), bean.getSpecimenRequest().getRowId()))%>">
                    change the request's status</a> to a non-final state.
    <%
            }
            else if (bean.isRequirementsComplete())
            {
    %>
                    This request's requirements are complete. Next steps include:<br>
                    <ul>
                        <li>Email specimen lists to their originating locations: <%= link("Originating Location Specimen Lists",
                            urlFor(LabSpecimenListsAction.class)
                                .addParameter("id", bean.getSpecimenRequest().getRowId())
                                .addParameter("listType", LabSpecimenListsBean.Type.ORIGINATING.toString())) %>
                        </li>
                        <li>Email specimen lists to their providing locations: <%= link("Providing Location Specimen Lists",
                            urlFor(LabSpecimenListsAction.class)
                                .addParameter("id", bean.getSpecimenRequest().getRowId())
                                .addParameter("listType", LabSpecimenListsBean.Type.PROVIDING.toString())) %>
                        </li>
                        <li>Update request status to indicate completion: <%= link("Update Request",
                                SpecimenController.getManageRequestStatusURL(getContainer(), bean.getSpecimenRequest().getRowId())) %>
                        </li>
                    </ul>
    <%
            }
    %>
                </td>
            </tr>
<%  }
    }
    if (notYetSubmitted)
    {
%>
        <tr class="labkey-wp-header">
            <th align="left">Unsubmitted Request</th>
        </tr>
        <tr>
            <td class="labkey-form-label"><span class="labkey-error"><b>This request has not been submitted yet.</b></span>
<%
        if (SpecimenRequestManager.get().hasEditRequestPermissions(user, bean.getSpecimenRequest()))
        {
%>
            <div style="padding-bottom: 0.5em">
<%
            List<Vial> vials = bean.getSpecimenRequest().getVials();
            if (!vials.isEmpty() || SpecimenService.get().getRequestCustomizer().allowEmptyRequests())
            {
%>
                Request processing will begin after the request has been submitted.<br><br>
                <%= button("Submit Request")
                        .href(urlFor(SubmitRequestAction.class).addParameter("id", bean.getSpecimenRequest().getRowId()))
                        .onClick("return LABKEY.Utils.confirmAndPost('" + StudyUtils.SUBMISSION_WARNING + "', '" + h(urlFor(SubmitRequestAction.class).addParameter("id", bean.getSpecimenRequest().getRowId())) + "')") %>
<%
            }
            else
            {
%>
                You must add specimens before submitting your request.<br><br>
                <%=specimenSearchButton%>
                <%=importVialIdsButton%>
<%
            }
%>
                <%= button("Cancel Request")
                        .onClick("return LABKEY.Utils.confirmAndPost('" + StudyUtils.CANCELLATION_WARNING + "', '" + h(urlFor(DeleteRequestAction.class).addParameter("id", bean.getSpecimenRequest().getRowId())) + "')") %>
<%
            if (bean.getReturnUrl() != null)
            {
%>
                <%= button("Return to Specimen View").href(bean.getReturnUrl()) %>
<%
            }
%>
                </div>
            </td>
        </tr>
<%
        }
        else
        {
%>
                Only the request creator (<%= h(creatingUser.getDisplayName(user)) %>) or an administrator may submit or cancel this request.
<%
        }
    }
    else // is submitted
    { %>
        <%= SpecimenService.get().getRequestCustomizer().getSubmittedMessage(getContainer(), bean.getSpecimenRequest().getRowId()) %><%
    }
%>
        <tr class="labkey-wp-header">
            <th align="left">Request Information</th>
        </tr>
        <tr>
            <td>
                <table class="table-condensed specimen-request-information">
                    <tr>
                        <th valign="top" align="right">Requester</th>
                        <td><%= h(creatingUser != null ? creatingUser.getDisplayName(user) : "Unknown") %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Requesting Location</th>
                        <td><%= h(destinationLocation != null ? destinationLocation.getDisplayName() : "Not specified") %></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Request Date</th>
                        <td><%=formatDateTime(bean.getSpecimenRequest().getCreated())%></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Description</th>
                        <td><%=HtmlString.unsafe(h(comments).toString().replaceAll("\\n", "<br>\n"))%></td>
                    </tr>
                    <tr>
                        <th valign="top" align="right">Status</th>
                        <td><%= h(bean.getStatus().getLabel()) %></td>
                    </tr>
                </table>
            </td>
        </tr>
<tr>
    <td>
        <%= link("View History", urlFor(RequestHistoryAction.class).addParameter("id", bean.getSpecimenRequest().getRowId())) %>&nbsp;
        <%= bean.isRequestManager() ? link("Update Request", SpecimenController.getManageRequestStatusURL(getContainer(), bean.getSpecimenRequest().getRowId())) : HtmlString.EMPTY_STRING %>
        <%
            if (hasExtendedRequestView)
            {
        %>
        <%= bean.isRequestManager() ? link("Update Extended Request", urlFor(ExtendedSpecimenRequestAction.class).addParameter("id", bean.getSpecimenRequest().getRowId())) : HtmlString.EMPTY_STRING %>
        <%
            }
        %>
        <%= bean.isRequestManager() ? link("Originating Location Specimen Lists",
                new ActionURL(LabSpecimenListsAction.class, c)
                    .addParameter("id", bean.getSpecimenRequest().getRowId())
                    .addParameter("listType", LabSpecimenListsBean.Type.ORIGINATING.toString())) : HtmlString.EMPTY_STRING %>
        <%= bean.isRequestManager() ? link("Providing Location Specimen Lists",
                new ActionURL(LabSpecimenListsAction.class, c)
                    .addParameter("id", bean.getSpecimenRequest().getRowId())
                    .addParameter("listType", LabSpecimenListsBean.Type.PROVIDING.toString())) : HtmlString.EMPTY_STRING %>
    </td>
</tr>
<labkey:form action="<%=urlFor(ManageRequestAction.class)%>" name="addRequirementForm" enctype="multipart/form-data" method="POST">
        <input type="hidden" name="id" value="<%= bean.getSpecimenRequest().getRowId()%>">
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
                            <th align=left><%= text(requirements.length > 0 ? "Status" : "") %></th>
                            <th align=left>&nbsp;</th>
                        </tr>
                    <%
                        for (SpecimenRequestRequirement requirement : requirements)
                        {
                            Location location = requirement.getLocation();
                            String siteLabel;
                            if (location == null)
                                siteLabel = "N/A";
                            else if (location.getLabel() == null)
                                siteLabel = "";
                            else
                                siteLabel = location.getDisplayName();
                    %>
                            <tr>
                                <td><%= h(requirement.getActor().getLabel()) %></td>
                                <td><%= h(siteLabel) %></td>
                                <td><%= requirement.getDescription() != null ? h(requirement.getDescription()) : HtmlString.NBSP %></td>
                                <td>
                                    <span class="<%= text(requirement.isComplete() ? "labkey-message" : "labkey-error")%>" style="font-weight:bold;">
                                        <%= text(requirement.isComplete() ? "Complete" : "Incomplete") %>
                                    </span>
                                </td>
                                <td>
                                    <%= link("Details", urlFor(ManageRequirementAction.class)
                                            .addParameter("id", requirement.getRequestId())
                                            .addParameter("requirementId", requirement.getRowId()))%>
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
                                        for (SpecimenRequestActor actor : actors)
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
                                        for (LocationImpl location : locations)
                                        {
                                    %>
                                    <option value="<%= location.getRowId() %>"><%= h(location.getDisplayName()) %></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </td>
                            <td colspan="2"><input type="text" name="newDescription" size="50"></td>
                            <td><%= button("Add Requirement").submit(true).onClick("if (validateNewRequirement()) document.addRequirementForm.submit(); return false;") %></td>
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
        </labkey:form>
<%
    if (null == bean.getSpecimenQueryView())
    {
%>
        <tr>
            <td>
                No specimens are associated with this request.<br>
                <%=specimenSearchButton%>
                <%=importVialIdsButton%>
            </td>
        </tr>
<%
    }
%>
    </table>
