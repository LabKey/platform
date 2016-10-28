<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.UserManager"%>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.study.Location"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.SpecimenManager" %>
<%@ page import="org.labkey.study.controllers.CreateChildStudyAction" %>
<%@ page import="org.labkey.study.controllers.specimen.ShowSearchAction" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.LocationImpl" %>
<%@ page import="org.labkey.study.model.SpecimenRequestActor" %>
<%@ page import="org.labkey.study.model.SpecimenRequestRequirement" %>
<%@ page import="org.labkey.study.model.SpecimenRequestStatus" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.Vial" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("FileUploadField.js");
        dependencies.add("study/StudyWizard.js");
    }
%>
<%
    JspView<SpecimenController.ManageRequestBean> me = (JspView<SpecimenController.ManageRequestBean>) HttpView.currentView();
    Container c = getContainer();
    User user = getUser();
    SpecimenController.ManageRequestBean bean = me.getModelBean();
    String comments = bean.getSpecimenRequest().getComments();
    if (comments == null)
        comments = "[No description provided]";
    SpecimenManager manager = SpecimenManager.getInstance();
    boolean hasExtendedRequestView = manager.getExtendedSpecimenRequestView(getViewContext()) != null;
    SpecimenRequestActor[] actors = manager.getRequirementsProvider().getActors(c);
    SpecimenRequestRequirement[] requirements = manager.getRequestRequirements(bean.getSpecimenRequest());
    Location destinationLocation = bean.getDestinationSite();
    User creatingUser = UserManager.getUser(bean.getSpecimenRequest().getCreatedBy());
    List<LocationImpl> locations = StudyManager.getInstance().getSites(c);
    boolean notYetSubmitted = false;
    if (manager.isSpecimenShoppingCartEnabled(c))
    {
        SpecimenRequestStatus cartStatus = manager.getRequestShoppingCartStatus(c, user);
        notYetSubmitted = bean.getSpecimenRequest().getStatusId() == cartStatus.getRowId();
    }

    String specimenSearchButton = manager.hasEditRequestPermissions(user, bean.getSpecimenRequest()) ?
        button("Specimen Search").href(buildURL(ShowSearchAction.class) + "showVials=true").toString() : "";
    String importVialIdsButton = manager.hasEditRequestPermissions(user, bean.getSpecimenRequest()) ?
        button("Upload Specimen Ids").href(buildURL(SpecimenController.ImportVialIdsAction.class, "id=" + bean.getSpecimenRequest().getRowId())).toString() : "";

    String availableStudyName = ContainerManager.getAvailableChildContainerName(c, "New Study");
%>
<script type="text/javascript">
    var NONSITE_ACTORS = [<%
    boolean first = true;
    for (SpecimenRequestActor actor : actors)
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

    function showNewStudyWizard()
    {
        Ext.onReady(function(){
            var wizard = new LABKEY.study.CreateStudyWizard({
                mode : 'specimen',
                allowRefresh : false,
                requestId : <%=bean.getSpecimenRequest().getRowId()%>,
                studyName : <%=q(availableStudyName)%>
            });

            wizard.on('success', function(info){}, this);

            // run the wizard
            wizard.show();
        });
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
                        .href(buildURL(SpecimenController.DeleteMissingRequestSpecimensAction.class, "id=" + bean.getSpecimenRequest().getRowId()))
                        .onClick("return confirm('Delete missing specimens?  This action cannot be undone.')") %><%
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
                To make changes, you must <a href="<%=h(buildURL(SpecimenController.ManageRequestStatusAction.class,"id=" + bean.getSpecimenRequest().getRowId()))%>">
                change the request's status</a> to a non-final state.
<%
        }
        else if (bean.isRequirementsComplete())
        {
%>
                This request's requirements are complete. Next steps include:<br>
                <ul>
                    <li>Email specimen lists to their originating locations: <%= textLink("Originating Location Specimen Lists",
                        new ActionURL(SpecimenController.LabSpecimenListsAction.class, c)
                                .addParameter("id", bean.getSpecimenRequest().getRowId())
                                .addParameter("listType", SpecimenController.LabSpecimenListsBean.Type.ORIGINATING.toString())) %>
                    </li>
                    <li>Email specimen lists to their providing locations: <%= textLink("Providing Location Specimen Lists",
                        new ActionURL(SpecimenController.LabSpecimenListsAction.class, c)
                                .addParameter("id", bean.getSpecimenRequest().getRowId())
                                .addParameter("listType", SpecimenController.LabSpecimenListsBean.Type.PROVIDING.toString())) %>
                    </li>
                    <li>Update request status to indicate completion: <%= textLink("Update Request",
                        new ActionURL(SpecimenController.ManageRequestStatusAction.class, c)
                                .addParameter("id", bean.getSpecimenRequest().getRowId())) %>
                    </li>
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
        if (manager.hasEditRequestPermissions(user, bean.getSpecimenRequest()))
        {
%>
            <div style="padding-bottom: 0.5em">
<%
            List<Vial> vials = bean.getSpecimenRequest().getVials();
            if (vials != null && vials.size() > 0)
            {
%>
                Request processing will begin after the request has been submitted.<br><br>
                <%= button("Submit Request")
                        .href(buildURL(SpecimenController.SubmitRequestAction.class) + "id=" + bean.getSpecimenRequest().getRowId())
                        .onClick("return confirm('" + SpecimenController.ManageRequestBean.SUBMISSION_WARNING + "')") %>
<%
            }
            else
            {
%>
                You must add specimens before submitting your request.<br><br>
                <%= text(specimenSearchButton) %>
                <%= text(importVialIdsButton) %>
<%
            }

            if (AppProps.getInstance().isExperimentalFeatureEnabled(CreateChildStudyAction.CREATE_SPECIMEN_STUDY))
            {
%>              <%= button("Create Study").href("javascript:void(0)").onClick("showNewStudyWizard();") %>
<%
            }
%>
                <%= button("Cancel Request")
                        .href(buildURL(SpecimenController.DeleteRequestAction.class, "id=" + bean.getSpecimenRequest().getRowId()))
                        .onClick("return confirm('" + SpecimenController.ManageRequestBean.CANCELLATION_WARNING + "')") %>
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
%>
        <tr class="labkey-wp-header">
            <th align="left">Request Information</th>
        </tr>
        <tr>
            <td>
                <table>
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
                        <td><%= h(comments).replaceAll("\\n", "<br>\n") %></td>
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
        <%= textLink("View History", new ActionURL(SpecimenController.RequestHistoryAction.class, c).addParameter("id", bean.getSpecimenRequest().getRowId())) %>&nbsp;
        <%= text(bean.isRequestManager() ? textLink("Update Request", new ActionURL(SpecimenController.ManageRequestStatusAction.class, c).addParameter("id", bean.getSpecimenRequest().getRowId())) : "") %>
        <%
            if (hasExtendedRequestView)
            {
        %>
        <%= text(bean.isRequestManager() ? textLink("Update Extended Request", new ActionURL(SpecimenController.ExtendedSpecimenRequestAction.class, c).addParameter("id", bean.getSpecimenRequest().getRowId())) : "") %>
        <%
            }
        %>
        <%= text(bean.isRequestManager() ? textLink("Originating Location Specimen Lists",
                    new ActionURL(SpecimenController.LabSpecimenListsAction.class, c)
                            .addParameter("id", bean.getSpecimenRequest().getRowId())
                            .addParameter("listType", SpecimenController.LabSpecimenListsBean.Type.ORIGINATING.toString())) : "") %>
        <%= text(bean.isRequestManager() ? textLink("Providing Location Specimen Lists",
                    new ActionURL(SpecimenController.LabSpecimenListsAction.class, c)
                            .addParameter("id", bean.getSpecimenRequest().getRowId())
                            .addParameter("listType", SpecimenController.LabSpecimenListsBean.Type.PROVIDING.toString())) : "") %>
    </td>
</tr>
<labkey:form action="<%=h(buildURL(SpecimenController.ManageRequestAction.class))%>" name="addRequirementForm" enctype="multipart/form-data" method="POST">
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
                                <td><%= text(requirement.getDescription() != null ? h(requirement.getDescription()) : "&nbsp;") %></td>
                                <td>
                                    <span class="<%= text(requirement.isComplete() ? "labkey-message" : "labkey-error")%>" style="font-weight:bold;">
                                        <%= text(requirement.isComplete() ? "Complete" : "Incomplete") %>
                                    </span>
                                </td>
                                <td>
                                    <%= textLink("Details", new ActionURL(SpecimenController.ManageRequirementAction.class, c)
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
                <%= text(specimenSearchButton) %>
                <%= text(importVialIdsButton) %>
            </td>
        </tr>
<%
    }
%>
    </table>
