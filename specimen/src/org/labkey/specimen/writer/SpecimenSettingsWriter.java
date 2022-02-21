/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
package org.labkey.specimen.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.GroupManager;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.settings.DisplaySettings;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.specimen.settings.StatusSettings;
import org.labkey.api.study.Location;
import org.labkey.api.study.Study;
import org.labkey.api.study.writer.SimpleStudyExportContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.security.xml.GroupType;
import org.labkey.security.xml.GroupsType;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.specimen.SpecimenRequestManager.SpecimenRequestInput;
import org.labkey.specimen.actions.ManageReqsBean;
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
        return SpecimenArchiveDataTypes.SPECIMEN_SETTINGS;
    }

    @Override
    public void write(Study study, SimpleStudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.Specimens specimensXml = ensureSpecimensElement(ctx);
        specimensXml.setSettings(DEFAULT_SETTINGS_FILE);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);
        writeSettings(study, ctx, vf);
    }

    private void writeSettings(Study study, SimpleStudyExportContext ctx, VirtualFile dir) throws Exception
    {
        SpecimensDocument xmlSettingsDoc = SpecimensDocument.Factory.newInstance();
        SpecimenSettingsType xmlSettings = xmlSettingsDoc.addNewSpecimens();
        RepositorySettings repositorySettings = SettingsManager.get().getRepositorySettings(study.getContainer());

        writeRepositoryType(xmlSettings, repositorySettings);
        writeLocationTypes(xmlSettings, study);
        writeSpecimenGroupings(xmlSettings, repositorySettings);
        writeDisplaySettings(xmlSettings, ctx);

        // these settings only apply if repository type is Advanced and specimen requests are enabled
        if (!repositorySettings.isSimple() && repositorySettings.isEnableRequests())
        {
            writeRequestStatuses(xmlSettings, study, ctx);
            writeActorsAndGroups(xmlSettings, study);
            writeDefaultRequirements(xmlSettings, study, ctx);
            writeRequestForm(xmlSettings, ctx);
            writeNotifications(xmlSettings, ctx);
            writeRequestabilityRules(xmlSettings, ctx);
        }

        // write out the xml
        dir.saveXmlBean(DEFAULT_SETTINGS_FILE, xmlSettingsDoc);
    }

    private void writeRepositoryType(SpecimenSettingsType specimenSettingsType, RepositorySettings repositorySettings)
    {
        specimenSettingsType.setRepositoryType(repositorySettings.isSimple() ? SpecimenRepositoryType.STANDARD : SpecimenRepositoryType.ADVANCED);
        specimenSettingsType.setEnableRequests(repositorySettings.isEnableRequests());
        specimenSettingsType.setEditableRepository(repositorySettings.isSpecimenDataEditable());
    }

    private void writeLocationTypes(SpecimenSettingsType specimenSettingsType, Study study)
    {
        SpecimenSettingsType.LocationTypes xmlLocationTypes = specimenSettingsType.addNewLocationTypes();
        xmlLocationTypes.addNewRepository().setAllowRequests(study.isAllowReqLocRepository());
        xmlLocationTypes.addNewClinic().setAllowRequests(study.isAllowReqLocClinic());
        xmlLocationTypes.addNewSiteAffiliatedLab().setAllowRequests(study.isAllowReqLocSal());
        xmlLocationTypes.addNewEndpointLab().setAllowRequests(study.isAllowReqLocEndpoint());
    }

    private void writeSpecimenGroupings(SpecimenSettingsType specimenSettingsType, RepositorySettings repositorySettings)
    {
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

    private void writeRequestStatuses(SpecimenSettingsType specimenSettingsType, Study study, SimpleStudyExportContext ctx)
    {
        SpecimenSettingsType.RequestStatuses xmlRequestStatuses = null;
        List<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(study.getContainer(), ctx.getUser());
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
        StatusSettings statusSettings = SettingsManager.get().getStatusSettings(study.getContainer());
        if (!statusSettings.isUseShoppingCart()) // default is to use shopping cart
        {
            if (xmlRequestStatuses == null) xmlRequestStatuses = specimenSettingsType.addNewRequestStatuses();
            xmlRequestStatuses.setMultipleSearch(statusSettings.isUseShoppingCart());
        }
    }

    private void writeActorsAndGroups(SpecimenSettingsType specimenSettingsType, Study study)
    {
        SpecimenRequestActor[] actors = SpecimenRequestRequirementProvider.get().getActors(study.getContainer());
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
                    for (Location location : study.getLocations())
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

    private void writeDefaultRequirements(SpecimenSettingsType specimenSettingsType, Study study, SimpleStudyExportContext ctx)
    {
        ManageReqsBean defRequirements = new ManageReqsBean(ctx.getUser(), study.getContainer());
        SpecimenSettingsType.DefaultRequirements xmlDefRequirements = null;
        if (defRequirements.getOriginatorRequirements().length > 0)
        {
            xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlOrigLabReq = xmlDefRequirements.addNewOriginatingLab();
            for (SpecimenRequestRequirement req : defRequirements.getOriginatorRequirements())
                writeDefaultRequirement(xmlOrigLabReq, req);
        }
        if (defRequirements.getProviderRequirements().length > 0)
        {
            if (xmlDefRequirements == null) xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlProviderReq = xmlDefRequirements.addNewProvidingLab();
            for (SpecimenRequestRequirement req : defRequirements.getProviderRequirements())
                writeDefaultRequirement(xmlProviderReq, req);
        }
        if (defRequirements.getReceiverRequirements().length > 0)
        {
            if (xmlDefRequirements == null) xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlReceiverReq = xmlDefRequirements.addNewReceivingLab();
            for (SpecimenRequestRequirement req : defRequirements.getReceiverRequirements())
                writeDefaultRequirement(xmlReceiverReq, req);
        }
        if (defRequirements.getGeneralRequirements().length > 0)
        {
            if (xmlDefRequirements == null) xmlDefRequirements = specimenSettingsType.addNewDefaultRequirements();
            DefaultRequirementsType xmlGeneralReq = xmlDefRequirements.addNewGeneral();
            for (SpecimenRequestRequirement req : defRequirements.getGeneralRequirements())
                writeDefaultRequirement(xmlGeneralReq, req);
        }
    }

    private void writeDefaultRequirement(DefaultRequirementsType xmlReqType, SpecimenRequestRequirement req)
    {
        DefaultRequirementType xmlReq = xmlReqType.addNewRequirement();
        xmlReq.setActor(req.getActor().getLabel());
        xmlReq.setDescription(req.getDescription());
    }

    private void writeActorGroup(SpecimenRequestActor actor, @Nullable Location location, GroupType xmlGroupType)
    {
        // Note: these actor groups only currently have Users (no groups within groups)
        GroupManager.exportGroupMembers(actor.getGroup(location), Collections.emptyList(), Arrays.asList(actor.getMembers(location)), xmlGroupType);

        // for a actor type of per location, use the location label as the group name
        // otherwise use the actor label in the per study case
        xmlGroupType.setName(location != null ? location.getLabel() : actor.getLabel());
    }

    private void writeDisplaySettings(SpecimenSettingsType specimenSettingsType, SimpleStudyExportContext ctx)
    {
        ctx.getLogger().info("Exporting specimen display settings");
        DisplaySettings settings = SettingsManager.get().getDisplaySettings(ctx.getContainer());

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

    private void writeRequestForm(SpecimenSettingsType specimenSettingsType, SimpleStudyExportContext ctx) throws SQLException
    {
        ctx.getLogger().info("Exporting specimen request forms");
        SpecimenRequestInput[] inputs = SpecimenRequestManager.get().getNewSpecimenRequestInputs(ctx.getContainer());

        if (inputs != null && inputs.length > 0)
        {
            SpecimenSettingsType.RequestForms forms = specimenSettingsType.addNewRequestForms();
            for (SpecimenRequestInput input : inputs)
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

    private void writeNotifications(SpecimenSettingsType specimenSettingsType, SimpleStudyExportContext ctx)
    {
        ctx.getLogger().info("Exporting specimen notification settings");
        RequestNotificationSettings notifications = SettingsManager.get().getRequestNotificationSettings(ctx.getContainer());
        SpecimenSettingsType.Notifications xmlNotifications = specimenSettingsType.addNewNotifications();

        if (notifications.getReplyTo() != null)
            xmlNotifications.setReplyTo(notifications.getReplyTo());
        if (notifications.getSubjectSuffix() != null)
            xmlNotifications.setSubjectSuffix(notifications.getSubjectSuffix());
        xmlNotifications.setNewRequestNotifyCheckbox(notifications.isNewRequestNotifyCheckbox());
        if (notifications.getNewRequestNotify() != null)
            xmlNotifications.setNewRequestNotify(notifications.getNewRequestNotify());
        xmlNotifications.setCcCheckbox(notifications.isCcCheckbox());
        if (notifications.getCc() != null)
            xmlNotifications.setCc(notifications.getCc());
        if (notifications.getDefaultEmailNotify() != null)
            xmlNotifications.setDefaultEmailNotify(notifications.getDefaultEmailNotify());
        if (notifications.getSpecimensAttachment() != null)
            xmlNotifications.setSpecimensAttachment(notifications.getSpecimensAttachment());
    }

    private void writeRequestabilityRules(SpecimenSettingsType specimenSettingsType, SimpleStudyExportContext ctx)
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
