/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.security.xml.GroupType;
import org.labkey.security.xml.GroupsType;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.importer.RequestabilityManager;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.SpecimenRequestActor;
import org.labkey.study.model.SpecimenRequestRequirement;
import org.labkey.study.model.SpecimenRequestStatus;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.specimen.settings.DisplaySettings;
import org.labkey.study.specimen.settings.RepositorySettings;
import org.labkey.study.specimen.settings.RequestNotificationSettings;
import org.labkey.study.specimen.settings.StatusSettings;
import org.labkey.study.xml.DefaultRequirementType;
import org.labkey.study.xml.DefaultRequirementsType;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: kevink
 * Date: 6/13/13
 */
public class SpecimenSettingsWriter extends AbstractSpecimenWriter
{
    private static final String DEFAULT_SETTINGS_FILE = "specimen_settings.xml";

    @Nullable
    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.SPECIMEN_SETTINGS;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.Specimens specimensXml = ensureSpecimensElement(ctx);
        specimensXml.setSettings(DEFAULT_SETTINGS_FILE);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);
        writeSettings(study, ctx, vf);
    }

    private void writeSettings(StudyImpl study, StudyExportContext ctx, VirtualFile dir) throws Exception
    {
        SpecimensDocument xmlSettingsDoc = SpecimensDocument.Factory.newInstance();
        SpecimenSettingsType xmlSettings = xmlSettingsDoc.addNewSpecimens();

        writeRepositoryType(xmlSettings, study, ctx);
        writeLocationTypes(xmlSettings, study, ctx);
        writeSpecimenGroupings(xmlSettings, study, ctx);
        writeDisplaySettings(xmlSettings, study, ctx);

        // these settings only apply if repository type is Advanced and specimen requests are enabled
        RepositorySettings repositorySettings = study.getRepositorySettings();
        if (!repositorySettings.isSimple() && repositorySettings.isEnableRequests())
        {
            writeRequestStatuses(xmlSettings, study, ctx);
            writeActorsAndGroups(xmlSettings, study, ctx);
            writeDefaultRequirements(xmlSettings, study, ctx);
            writeRequestForm(xmlSettings, study, ctx);
            writeNotifications(xmlSettings, study, ctx);
            writeRequestabilityRules(xmlSettings, study, ctx);
        }

        // write out the xml
        dir.saveXmlBean(DEFAULT_SETTINGS_FILE, xmlSettingsDoc);
    }

    private void writeRepositoryType(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        RepositorySettings repositorySettings = study.getRepositorySettings();

        specimenSettingsType.setRepositoryType(repositorySettings.isSimple() ? SpecimenRepositoryType.STANDARD : SpecimenRepositoryType.ADVANCED);
        specimenSettingsType.setEnableRequests(repositorySettings.isEnableRequests());
        specimenSettingsType.setEditableRepository(repositorySettings.isSpecimenDataEditable());
    }

    private void writeLocationTypes(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        SpecimenSettingsType.LocationTypes xmlLocationTypes = specimenSettingsType.addNewLocationTypes();
        xmlLocationTypes.addNewRepository().setAllowRequests(study.isAllowReqLocRepository());
        xmlLocationTypes.addNewClinic().setAllowRequests(study.isAllowReqLocClinic());
        xmlLocationTypes.addNewSiteAffiliatedLab().setAllowRequests(study.isAllowReqLocSal());
        xmlLocationTypes.addNewEndpointLab().setAllowRequests(study.isAllowReqLocEndpoint());
    }

    private void writeSpecimenGroupings(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        RepositorySettings repositorySettings = study.getRepositorySettings();
        ArrayList<String[]> groupings = repositorySettings.getSpecimenWebPartGroupings();
        if (groupings.size() > 0)
        {
            SpecimenSettingsType.WebPartGroupings xmlWebPartGroupings = specimenSettingsType.addNewWebPartGroupings();
            for (String[] grouping : groupings)
            {
                SpecimenSettingsType.WebPartGroupings.Grouping xmlGrouping = xmlWebPartGroupings.addNewGrouping();
                xmlGrouping.setGroupByArray(grouping);
            }
        }
    }

    private void writeRequestStatuses(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        SpecimenSettingsType.RequestStatuses xmlRequestStatuses = null;
        List<SpecimenRequestStatus> statuses = study.getSampleRequestStatuses(ctx.getUser());
        if (statuses.size() > 0)
        {
            for (SpecimenRequestStatus status : statuses)
            {
                if (!status.isSystemStatus()) // don't export system statuses
                {
                    if (xmlRequestStatuses == null) xmlRequestStatuses = specimenSettingsType.addNewRequestStatuses();
                    SpecimenSettingsType.RequestStatuses.Status xmlStatus = xmlRequestStatuses.addNewStatus();
                    xmlStatus.setLabel(status.getLabel());
                    xmlStatus.setFinalState(status.isFinalState());
                    xmlStatus.setLockSpecimens(status.isSpecimensLocked());
                }
            }
        }
        StatusSettings statusSettings = SpecimenManager.getInstance().getStatusSettings(study.getContainer());
        if (!statusSettings.isUseShoppingCart()) // default is to use shopping cart
        {
            if (xmlRequestStatuses == null) xmlRequestStatuses = specimenSettingsType.addNewRequestStatuses();
            xmlRequestStatuses.setMultipleSearch(statusSettings.isUseShoppingCart());
        }
    }

    private void writeActorsAndGroups(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        SpecimenRequestActor[] actors = study.getSampleRequestActors();
        if (actors != null && actors.length > 0)
        {
            SpecimenSettingsType.RequestActors xmlRequestActors = specimenSettingsType.addNewRequestActors();
            for (SpecimenRequestActor actor : actors)
            {
                SpecimenSettingsType.RequestActors.Actor xmlActor = xmlRequestActors.addNewActor();
                xmlActor.setLabel(actor.getLabel());
                xmlActor.setType(actor.isPerSite() ? SpecimenSettingsType.RequestActors.Actor.Type.LOCATION : SpecimenSettingsType.RequestActors.Actor.Type.STUDY);

                GroupsType xmlGroups = xmlActor.addNewGroups();
                if (!actor.isPerSite())
                {
                    if (actor.getMembers().length > 0)
                    {
                        GroupType xmlGroup = xmlGroups.addNewGroup();
                        writeActorGroup(actor, null, xmlGroup);
                    }
                }
                else
                {
                    for (LocationImpl location : study.getLocations())
                    {
                        if (actor.getMembers(location).length > 0)
                        {
                            GroupType xmlGroup = xmlGroups.addNewGroup();
                            writeActorGroup(actor, location, xmlGroup);
                        }
                    }
                }
            }
        }
    }

    private void writeDefaultRequirements(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx) throws SQLException
    {
        SpecimenController.ManageReqsBean defRequirments = new SpecimenController.ManageReqsBean(ctx.getUser(), study.getContainer());
        SpecimenSettingsType.DefaultRequirements xmlDefRequirements = null;
        if (defRequirments.getOriginatorRequirements().length > 0)
        {
            xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlOrigLabReq = xmlDefRequirements.addNewOriginatingLab();
            for (SpecimenRequestRequirement req : defRequirments.getOriginatorRequirements())
                writeDefaultRequirement(xmlOrigLabReq, req);
        }
        if (defRequirments.getProviderRequirements().length > 0)
        {
            if (xmlDefRequirements == null) xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlProviderReq = xmlDefRequirements.addNewProvidingLab();
            for (SpecimenRequestRequirement req : defRequirments.getProviderRequirements())
                writeDefaultRequirement(xmlProviderReq, req);
        }
        if (defRequirments.getReceiverRequirements().length > 0)
        {
            if (xmlDefRequirements == null) xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlReceiverReq = xmlDefRequirements.addNewReceivingLab();
            for (SpecimenRequestRequirement req : defRequirments.getReceiverRequirements())
                writeDefaultRequirement(xmlReceiverReq, req);
        }
        if (defRequirments.getGeneralRequirements().length > 0)
        {
            if (xmlDefRequirements == null) xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlGeneralReq = xmlDefRequirements.addNewGeneral();
            for (SpecimenRequestRequirement req : defRequirments.getGeneralRequirements())
                writeDefaultRequirement(xmlGeneralReq, req);
        }
    }

    private void writeDefaultRequirement(DefaultRequirementsType xmlReqType, SpecimenRequestRequirement req)
    {
        DefaultRequirementType xmlReq = xmlReqType.addNewRequirement();
        xmlReq.setActor(req.getActor().getLabel());
        xmlReq.setDescription(req.getDescription());
    }

    private void writeActorGroup(SpecimenRequestActor actor, @Nullable LocationImpl location, GroupType xmlGroupType)
    {
        // Note: these actor groups only currently have Users (no groups within groups)
        GroupManager.exportGroupMembers(actor.getGroup(location), Collections.emptyList(), Arrays.asList(actor.getMembers(location)), xmlGroupType);

        // for a actor type of per location, use the location label as the group name
        // otherwise use the actor label in the per study case
        xmlGroupType.setName(location != null ? location.getLabel() : actor.getLabel());
    }

    private void writeDisplaySettings(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        ctx.getLogger().info("Exporting specimen display settings");
        DisplaySettings settings = SpecimenManager.getInstance().getDisplaySettings(ctx.getContainer());

        SpecimenSettingsType.DisplaySettings xmlSettings = specimenSettingsType.addNewDisplaySettings();

        SpecimenSettingsType.DisplaySettings.CommentsAndQC commentsAndQC = xmlSettings.addNewCommentsAndQC();
        commentsAndQC.setDefaultToCommentsMode(settings.isDefaultToCommentsMode());
        commentsAndQC.setEnableManualQCFlagging(settings.isEnableManualQCFlagging());

        SpecimenSettingsType.DisplaySettings.LowSpecimenWarnings warnings = xmlSettings.addNewLowSpecimenWarnings();
        if (settings.getLastVial() != null)
            warnings.setLastVial(settings.getLastVial());
        if (settings.getZeroVials() != null)
            warnings.setZeroVials(settings.getZeroVials());
    }

    private void writeRequestForm(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx) throws SQLException
    {
        ctx.getLogger().info("Exporting specimen request forms");
        SpecimenManager.SpecimenRequestInput[] inputs = SpecimenManager.getInstance().getNewSpecimenRequestInputs(ctx.getContainer());

        if (inputs != null && inputs.length > 0)
        {
            SpecimenSettingsType.RequestForms forms = specimenSettingsType.addNewRequestForms();
            for (SpecimenManager.SpecimenRequestInput input : inputs)
            {
                SpecimenSettingsType.RequestForms.Form requestForm = forms.addNewForm();

                if (input.getTitle() != null)
                    requestForm.setTitle(input.getTitle());
                if (input.getHelpText() != null)
                    requestForm.setHelpText(input.getHelpText());
                requestForm.setMultiLine(input.isMultiLine());
                requestForm.setRequired(input.isRequired());
                requestForm.setRememberSiteValue(input.isRememberSiteValue());
                requestForm.setDisplayOrder(input.getDisplayOrder());
            }
        }
    }

    private void writeNotifications(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        ctx.getLogger().info("Exporting specimen notification settings");
        RequestNotificationSettings notifications = SpecimenManager.getInstance().getRequestNotificationSettings(ctx.getContainer());
        SpecimenSettingsType.Notifications xmlNotificatons = specimenSettingsType.addNewNotifications();

        if (notifications.getReplyTo() != null)
            xmlNotificatons.setReplyTo(notifications.getReplyTo());
        if (notifications.getSubjectSuffix() != null)
            xmlNotificatons.setSubjectSuffix(notifications.getSubjectSuffix());
        xmlNotificatons.setNewRequestNotifyCheckbox(notifications.isNewRequestNotifyCheckbox());
        if (notifications.getNewRequestNotify() != null)
            xmlNotificatons.setNewRequestNotify(notifications.getNewRequestNotify());
        xmlNotificatons.setCcCheckbox(notifications.isCcCheckbox());
        if (notifications.getCc() != null)
            xmlNotificatons.setCc(notifications.getCc());
        if (notifications.getDefaultEmailNotify() != null)
            xmlNotificatons.setDefaultEmailNotify(notifications.getDefaultEmailNotify());
        if (notifications.getSpecimensAttachment() != null)
            xmlNotificatons.setSpecimensAttachment(notifications.getSpecimensAttachment());
    }

    private void writeRequestabilityRules(SpecimenSettingsType specimenSettingsType, StudyImpl study, StudyExportContext ctx)
    {
        ctx.getLogger().info("Exporting specimen requestability rules");
        List<RequestabilityManager.RequestableRule> rules = RequestabilityManager.getInstance().getRules(ctx.getContainer());
        if (!rules.isEmpty())
        {
            SpecimenSettingsType.RequestabilityRules rulesXml = specimenSettingsType.addNewRequestabilityRules();
            for (RequestabilityManager.RequestableRule rule : rules)
            {
                SpecimenSettingsType.RequestabilityRules.Rule xmlRule = rulesXml.addNewRule();

                if (rule.getType() != null)
                    xmlRule.setType(rule.getType().name());
                if (rule.getRuleData() != null)
                    xmlRule.setRuleData(rule.getRuleData());
            }
        }
    }
}
