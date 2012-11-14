<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Site"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.study.model.SampleRequestActor" %>
<%@ page import="org.labkey.study.model.SampleRequestRequirement" %>
<%@ page import="org.labkey.study.model.SampleRequestStatus" %>
<%@ page import="org.labkey.study.model.SiteImpl" %>
<%@ page import="org.labkey.study.model.Specimen" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.controllers.samples.ShowSearchAction" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.study.controllers.CreateChildStudyAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>

<%!
    public LinkedHashSet<ClientDependency> getClientDependencies(){
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext3"));
        resources.add(ClientDependency.fromFilePath("FileUploadField.js"));
        resources.add(ClientDependency.fromFilePath("study/StudyWizard.js"));
        return resources;
    }
%>
<%

    JspView<SpecimenController.ManageRequestBean> me = (JspView<SpecimenController.ManageRequestBean>) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    SpecimenController.ManageRequestBean bean = me.getModelBean();
    String comments = bean.getSampleRequest().getComments();
    ViewContext context = me.getViewContext();
    if (comments == null)
        comments = "[No description provided]";
    SampleRequestActor[] actors = SampleManager.getInstance().getRequirementsProvider().getActors(context.getContainer());
    SampleRequestRequirement[] requirements = SampleManager.getInstance().getRequestRequirements(bean.getSampleRequest());
    Site destinationSite = bean.getDestinationSite();
    User creatingUser = UserManager.getUser(bean.getSampleRequest().getCreatedBy());
    SiteImpl[] sites = StudyManager.getInstance().getSites(context.getContainer());
    boolean notYetSubmitted = false;
    if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer()))
    {
        SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(context.getContainer(), context.getUser());
        notYetSubmitted = bean.getSampleRequest().getStatusId() == cartStatus.getRowId();
    }

    String specimenSearchButton = SampleManager.getInstance().hasEditRequestPermissions(context.getUser(), bean.getSampleRequest()) ?
        generateButton("Specimen Search", buildURL(ShowSearchAction.class) + "showVials=true", null) : "";
    String importVialIdsButton = SampleManager.getInstance().hasEditRequestPermissions(context.getUser(), bean.getSampleRequest()) ?
        generateButton("Upload Specimen Ids", buildURL(SpecimenController.ImportVialIdsAction.class,
                "id=" + bean.getSampleRequest().getRowId()), null) : "";

    Map<String, Container> folders = new HashMap<String, Container>();
    for (Container child : ContainerManager.getChildren(c))
        folders.put(child.getName(), child);

    String ancillaryStudyName = "New Study";
    int i = 1;
    while (folders.containsKey(ancillaryStudyName))
    {
        ancillaryStudyName = "New Study " + i++;
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
    setCookieToRequestId(<%= bean.getSampleRequest().getRowId()%>);


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
        var init = function(){
            var wizard = new LABKEY.study.CreateStudyWizard({
                requestId : <%=bean.getSampleRequest().getRowId()%>,
                studyName : <%=q(ancillaryStudyName)%>,
                namePanel : true,
                datasetsPanel : true
            });

            wizard.on('success', function(info){}, this);

            // run the wizard
            wizard.show();
        };
        Ext.onReady(init);
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
                <%= generateButton("Delete missing specimens",
                        buildURL(SpecimenController.DeleteMissingRequestSpecimensAction.class, "id=" + bean.getSampleRequest().getRowId()),
                        "return confirm('Delete missing specimens?  This action cannot be undone.')")%><%
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
                To make changes, you must <a href="<%=buildURL(SpecimenController.ManageRequestStatusAction.class,"id=" + bean.getSampleRequest().getRowId())%>">
                change the request's status</a> to a non-final state.
<%
        }
        else if (bean.isRequirementsComplete())
        {
%>
                This request's requirements are complete. Next steps include:<br>
                <ul>
                    <li>Email specimen lists to their originating locations: <%= textLink("Originating Location Specimen Lists",
                        buildURL(SpecimenController.LabSpecimenListsAction.class, "id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpecimenController.LabSpecimenListsBean.Type.ORIGINATING.toString())) %></li>
                    <li>Email specimen lists to their providing locations: <%= textLink("Providing Location Specimen Lists",
                        buildURL(SpecimenController.LabSpecimenListsAction.class, "id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpecimenController.LabSpecimenListsBean.Type.PROVIDING.toString())) %></li>
                    <li>Update request status to indicate completion: <%= textLink("Update Request", buildURL(SpecimenController.ManageRequestStatusAction.class,"id=" + bean.getSampleRequest().getRowId())) %></li>
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
%>
            <div style="padding-bottom: 0.5em">
<%
            Specimen[] specimens = bean.getSampleRequest().getSpecimens();
            if (specimens != null && specimens.length > 0)
            {
%>
                Request processing will begin after the request has been submitted.<br><br>
                <%= generateButton("Submit Request", buildURL(SpecimenController.SubmitRequestAction.class) + "id=" + bean.getSampleRequest().getRowId(),
                        "return confirm('" + SpecimenController.ManageRequestBean.SUBMISSION_WARNING + "')")%>
<%
            }
            else
            {
%>
                You must add specimens before submitting your request.<br><br>
                <%= specimenSearchButton %>
                <%= importVialIdsButton %>
<%
            }

            if (AppProps.getInstance().isExperimentalFeatureEnabled(CreateChildStudyAction.CREATE_SPECIMEN_STUDY))
            {
%>              <%=generateButton("Create Study", "javascript:void(0)", "showNewStudyWizard()")%>
<%
            }
%>
                <%= generateButton("Cancel Request", buildURL(SpecimenController.DeleteRequestAction.class, "id=" + bean.getSampleRequest().getRowId()),
                        "return confirm('" + SpecimenController.ManageRequestBean.CANCELLATION_WARNING + "')")%>
<%
            if (bean.getReturnUrl() != null)
            {
%>
                <%= generateButton("Return to Specimen View", bean.getReturnUrl())%>
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
                Only the request creator (<%= creatingUser.getDisplayName(context.getUser()) %>) or an administrator may submit or cancel this request.
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
                        <td><%= creatingUser != null ? h(creatingUser.getDisplayName(context.getUser())) : "Unknown" %></td>
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
                            <%= textLink("View History", buildURL(SpecimenController.RequestHistoryAction.class) + "id=" + bean.getSampleRequest().getRowId()) %>&nbsp;
                            <%= bean.isRequestManager() ? textLink("Update Request", buildURL(SpecimenController.ManageRequestStatusAction.class) + "id=" + bean.getSampleRequest().getRowId()) : "" %>
                            <%= bean.isRequestManager() ? textLink("Originating Location Specimen Lists",
                                    buildURL(SpecimenController.LabSpecimenListsAction.class) + "id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpecimenController.LabSpecimenListsBean.Type.ORIGINATING.toString()) : "" %>
                            <%= bean.isRequestManager() ? textLink("Providing Location Specimen Lists",
                                    buildURL(SpecimenController.LabSpecimenListsAction.class) + "id=" + bean.getSampleRequest().getRowId() + "&listType=" + SpecimenController.LabSpecimenListsBean.Type.PROVIDING.toString()) : "" %>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <form action="<%=h(buildURL(SpecimenController.ManageRequestAction.class))%>" name="addRequirementForm" enctype="multipart/form-data" method="POST">
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
                                    <%= textLink("Details", buildURL(SpecimenController.ManageRequirementAction.class) + "id=" + requirement.getRequestId() + "&requirementId=" + requirement.getRowId())%>
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
                                        for (SiteImpl site : sites)
                                        {
                                    %>
                                    <option value="<%= site.getRowId() %>"><%= h(site.getDisplayName()) %></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </td>
                            <td colspan="2"><input type="text" name="newDescription" size="50"></td>
                            <td><%= buttonImg("Add Requirement", "if (validateNewRequirement()) document.addRequirementForm.submit(); return false;")%></td>
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
                <%= specimenSearchButton %>
                <%= importVialIdsButton %>
                <%
                    }
                %>
            </td>
        </tr>
    </table>
