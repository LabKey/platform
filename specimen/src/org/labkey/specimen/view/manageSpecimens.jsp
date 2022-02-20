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
<%@ page import="com.google.common.collect.Iterables"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.specimen.SpecimenRequestManager" %>
<%@ page import="org.labkey.api.specimen.model.SpecimenTablesProvider" %>
<%@ page import="org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider" %>
<%@ page import="org.labkey.api.specimen.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="org.labkey.api.specimen.settings.SettingsManager" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.SpecimenTransform" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ChooseImporterAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ConfigureRequestabilityRulesAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageActorsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageDefaultReqsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageDisplaySettingsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageLocationTypesAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageNotificationsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageRepositorySettingsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageRequestInputsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageSpecimenCommentsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageSpecimenWebPartAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.ManageStatusesAction" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    Study study = StudyService.get().getStudy(c);
    User user = getUser();
    String subjectNounSingle = study.getSubjectNounSingular().toLowerCase();

    if (c.hasPermission(user, AdminPermission.class))
    {
        if (!study.hasSourceStudy() && !study.isSnapshotStudy())
        {
            SpecimenTablesProvider stp = new SpecimenTablesProvider(c, user, null);
            Domain domainEvent = stp.getDomain("specimenevent",false);
            Domain domainVial = stp.getDomain("vial",false);
            Domain domainSpecimen = stp.getDomain("specimen",false);

            ActionURL specimenEventUrl = null;
            ActionURL vialUrl = null;
            ActionURL specimenUrl = null;

            Collection<SpecimenTransform> specimenTransforms = SpecimenService.get().getSpecimenTransforms(getContainer());
            specimenTransforms.removeIf(transform -> null == transform.getManageAction(getContainer(), getUser()));
            int numberOfTransforms = specimenTransforms.size();

            if (domainEvent != null)
            {
                specimenEventUrl = domainEvent.getDomainKind().urlEditDefinition(domainEvent, getViewContext())
                    .addReturnURL(getViewContext().getActionURL());
            }

            if (domainVial != null)
            {
                vialUrl = domainVial.getDomainKind().urlEditDefinition(domainVial, getViewContext())
                    .addReturnURL(getViewContext().getActionURL());
            }

            if (domainSpecimen != null)
            {
                specimenUrl = domainSpecimen.getDomainKind().urlEditDefinition(domainSpecimen, getViewContext())
                    .addReturnURL(getViewContext().getActionURL());
            }

%>
            <labkey:panel title="Specimen Repository Settings">
                <table class="lk-fields-table">
                    <tr>
                        <td class="lk-study-prop-label">Repository Type</td>
                        <td class="lk-study-prop-desc">This study uses the <%=text(SettingsManager.get().getRepositorySettings(study.getContainer()).isSimple() ? "standard" : "advanced")%> specimen repository</td>
                        <td><%=link("Change Repository Type", ManageRepositorySettingsAction.class)%></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Specimen Fields</td>
                        <td class="lk-study-prop-desc">Customize specimen fields for this repository</td>
                        <td><%=link("Edit Specimen fields", specimenUrl)%></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Vial Fields</td>
                        <td class="lk-study-prop-desc">Customize vial fields for this repository</td>
                        <td><%=link("Edit Vial fields", vialUrl)%></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Specimen Event Fields</td>
                        <td class="lk-study-prop-desc">Customize specimen event fields for this repository</td>
                        <td><%=link("Edit Specimen Event fields", specimenEventUrl)%></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Display and Behavior</td>
                        <td class="lk-study-prop-desc">Manage warnings, comments, and workflow</td>
                        <td><%= link("Manage Display and Behavior", ManageDisplaySettingsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Comments</td>
                        <td class="lk-study-prop-desc">Manage <%=h(subjectNounSingle)%> and <%=h(subjectNounSingle)%>/visit comments</td>
                        <td><%= link("Manage Comments", ManageSpecimenCommentsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Specimen Web Part</td>
                        <td class="lk-study-prop-desc">Configure the specimen groupings in the specimen web part</td>
                        <td><%= link("Configure Specimen Groupings", ManageSpecimenWebPartAction.class) %></td>
                    </tr>

                    <% if (numberOfTransforms == 1) { %>
                    <%
                        SpecimenTransform transform = Iterables.get(specimenTransforms, 0);
                        ActionURL manageAction = transform.getManageAction(getContainer(), getUser());
                    %>
                        <tr>
                            <td class="lk-study-prop-label">Specimen Import</td>
                            <td class="lk-study-prop-desc">Configure a specimen import</td>
                            <td><%=link("Configure specimen import", manageAction)%></td>
                        </tr>
                    <% } else { %>
                        <tr>
                            <td class="lk-study-prop-label">Specimen Import</td>
                            <td class="lk-study-prop-desc">Choose and configure a specimen import</td>
                            <td><%=link("Configure specimen import", ChooseImporterAction.class)%></td>
                        </tr>
                    <%
                        }
                    %>
                </table>
            </labkey:panel>
        <%
        }
        else
        {
            String childStudyType = study.getStudySnapshotType().getTitle().toLowerCase();
        %>
        <p><em>Note: specimen repository and request settings are not available for <%=text(childStudyType)%> studies.</em></p>
        <%
        }
    } // admin permission

    if (c.hasPermission(user, ManageRequestSettingsPermission.class))
    {
        if (SettingsManager.get().getRepositorySettings(getContainer()).isEnableRequests())
        {
    %>
            <labkey:panel title="Specimen Request Settings">
                <table class="lk-fields-table">
                    <tr>
                        <td class="lk-study-prop-label">Location Types</td>
                        <td class="lk-study-prop-desc">Configure which location types are allowed to be requesting locations</td>
                        <td><%= link("Manage Location Types", ManageLocationTypesAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Statuses</td>
                        <td class="lk-study-prop-desc">This study defines <%=SpecimenRequestManager.get().getRequestStatuses(study.getContainer(), getUser()).size() %> specimen request
                            statuses</td>
                        <td><%= link("Manage Request Statuses", ManageStatusesAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Actors</td>
                        <td class="lk-study-prop-desc">This study defines <%=SpecimenRequestRequirementProvider.get().getActors(getContainer()).length %> specimen request
                            actors</td>
                        <td><%= link("Manage Actors and Groups", ManageActorsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Request Requirements</td>
                        <td class="lk-study-prop-desc">Manage default requirements for new requests</td>
                        <td><%= link("Manage Default Requirements", ManageDefaultReqsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Request Form</td>
                        <td class="lk-study-prop-desc">Manage inputs required for a new specimen request </td>
                        <td><%= link("Manage New Request Form", ManageRequestInputsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Notifications</td>
                        <td class="lk-study-prop-desc">Manage specimen request notifications</td>
                        <td><%= link("Manage Notifications", ManageNotificationsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Requestability Rules</td>
                        <td class="lk-study-prop-desc">Manage the rules used to determine specimen availability for request</td>
                        <td><%= link("Manage Requestability Rules", ConfigureRequestabilityRulesAction.class) %></td>
                    </tr>
                </table>
            </labkey:panel>
<%
        }
    }
%>
