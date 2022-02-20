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
package org.labkey.specimen.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenRequestManager.SpecimenRequestInput;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.location.LocationCache;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.requirements.RequirementType;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementType;
import org.labkey.api.specimen.settings.DisplaySettings;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.specimen.settings.StatusSettings;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.study.importer.SimpleStudyImporter;
import org.labkey.api.writer.VirtualFile;
import org.labkey.security.xml.GroupType;
import org.labkey.specimen.actions.ManageReqsBean;
import org.labkey.specimen.writer.SpecimenArchiveDataTypes;
import org.labkey.study.xml.DefaultRequirementType;
import org.labkey.study.xml.DefaultRequirementsType;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 6/13/13
 */
public class SpecimenSettingsImporter implements SimpleStudyImporter
{
    @Override
    public Timing getTiming()
    {
        return Timing.Late; // Can't setup specimen request actors until the locations have been created
    }

    @Override
    public void preHandling(SimpleStudyImportContext ctx)
    {
    }

    @Override
    public void postHandling(SimpleStudyImportContext ctx)
    {
    }

    @Override
    public String getDescription()
    {
        return "specimen settings";
    }

    @Override
    public String getDataType()
    {
        return SpecimenArchiveDataTypes.SPECIMEN_SETTINGS;
    }

    @Override
    public void process(SimpleStudyImportContext ctx, VirtualFile studyDir, BindException errors) throws SQLException, ImportException, IOException
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, studyDir))
        {
            StudyDocument.Study.Specimens xmlSettings = ctx.getXml().getSpecimens();

            if (xmlSettings.getSettings() != null)
            {
                ctx.getLogger().info("Loading " + getDescription());

                // Import specimen settings from specimen_settings.xml doc
                VirtualFile settingsDir = studyDir;
                if (xmlSettings.getDir() != null)
                    settingsDir = studyDir.getDir(xmlSettings.getDir());

                SpecimensDocument specimensDoc = (SpecimensDocument)settingsDir.getXmlBean(xmlSettings.getSettings());
                importSettings(ctx, specimensDoc.getSpecimens());

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }
    }

    @Override
    public boolean isValidForImportArchive(SimpleStudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getSpecimens() != null;
    }

    // Import specimen settings from specimen_settings.xml
    private void importSettings(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings) throws SQLException
    {
        Container c = ctx.getContainer();
        RepositorySettings reposSettings = SettingsManager.get().getRepositorySettings(c);

        // webpart groupings
        SpecimenSettingsType.WebPartGroupings xmlWebPartGroupings = xmlSettings.getWebPartGroupings();
        if (null != xmlWebPartGroupings)
        {
            SpecimenSettingsType.WebPartGroupings.Grouping[] xmlGroupings = xmlWebPartGroupings.getGroupingArray();
            if (null != xmlGroupings)
            {
                ArrayList<String[]> groupings = new ArrayList<>(2);
                for (SpecimenSettingsType.WebPartGroupings.Grouping xmlGrouping : xmlGroupings)
                {
                    String[] groupBys = xmlGrouping.getGroupByArray();
                    groupings.add(groupBys);
                }
                reposSettings.setSpecimenWebPartGroupings(groupings);
            }
        }

        // repository type
        SpecimenRepositoryType.Enum repositoryType = xmlSettings.getRepositoryType();
        boolean simple = (SpecimenRepositoryType.STANDARD == repositoryType);
        reposSettings.setSimple(simple);
        if (xmlSettings.isSetEnableRequests())
            reposSettings.setEnableRequests(xmlSettings.getEnableRequests());
        if (xmlSettings.isSetEditableRepository())
            reposSettings.setSpecimenDataEditable(xmlSettings.getEditableRepository());
        SettingsManager.get().saveRepositorySettings(c, reposSettings);

        // location types
        SpecimenSettingsType.LocationTypes xmlLocationTypes = xmlSettings.getLocationTypes();
        if (null != xmlLocationTypes)
        {
            Boolean repo = xmlLocationTypes.isSetRepository() && xmlLocationTypes.getRepository().isSetAllowRequests() ? xmlLocationTypes.getRepository().getAllowRequests() : null;
            Boolean clinic = xmlLocationTypes.isSetClinic() && xmlLocationTypes.getClinic().isSetAllowRequests() ? xmlLocationTypes.getClinic().getAllowRequests() : null;
            Boolean sal = xmlLocationTypes.isSetSiteAffiliatedLab() && xmlLocationTypes.getSiteAffiliatedLab().isSetAllowRequests() ? xmlLocationTypes.getSiteAffiliatedLab().getAllowRequests() : null;
            Boolean endpoint = xmlLocationTypes.isSetEndpointLab() && xmlLocationTypes.getEndpointLab().isSetAllowRequests() ? xmlLocationTypes.getEndpointLab().getAllowRequests() : null;

            StudyService.get().saveLocationSettings(ctx.getStudy(), ctx.getUser(), repo, clinic, sal, endpoint);
        }

        importRequestStatuses(ctx, xmlSettings);
        importRequestActors(ctx, xmlSettings);
        importDefaultRequirements(ctx, xmlSettings);
        importDisplaySettings(ctx, xmlSettings);
        importRequestForm(ctx, xmlSettings);
        importNotifications(ctx, xmlSettings);
        importRequestabilityRules(ctx, xmlSettings);
    }

    private void importRequestStatuses(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        SpecimenSettingsType.RequestStatuses xmlRequestStatuses = xmlSettings.getRequestStatuses();
        if (xmlRequestStatuses != null)
        {
            SpecimenSettingsType.RequestStatuses.Status[] xmlStatusArray = xmlRequestStatuses.getStatusArray();
            if (xmlStatusArray.length > 0)
            {
                // remove any existing not in-use, non-system statuses for this container before importing the new ones
                Set<Integer> inUseStatusIds = SpecimenRequestManager.get().getRequestStatusIdsInUse(ctx.getContainer());
                List<String> inUseStatusLabels = new ArrayList<>();
                for (SpecimenRequestStatus existingStatus : SpecimenRequestManager.get().getRequestStatuses(ctx.getContainer(), ctx.getUser()))
                {
                    if (!existingStatus.isSystemStatus() && !inUseStatusIds.contains(existingStatus.getRowId()))
                        SpecimenRequestManager.get().deleteRequestStatus(existingStatus);
                    else
                        inUseStatusLabels.add(existingStatus.getLabel());
                }

                // create new request statuses from the xml settings file
                for (int i = 0; i < xmlStatusArray.length; i++)
                {
                    String newStatusLabel = xmlStatusArray[i].isSetLabel() && xmlStatusArray[i].getLabel() != null && !xmlStatusArray[i].getLabel().isEmpty() ? xmlStatusArray[i].getLabel() : null;
                    if (inUseStatusLabels.contains(newStatusLabel))
                    {
                        ctx.getLogger().warn("Skipping request status that matches an existing status label: " + newStatusLabel);
                    }
                    else if (newStatusLabel != null)
                    {
                        SpecimenRequestStatus newStatus = new SpecimenRequestStatus();
                        newStatus.setContainer(ctx.getContainer());
                        newStatus.setLabel(newStatusLabel);
                        newStatus.setSortOrder(i+1);
                        if (xmlStatusArray[i].isSetFinalState())
                            newStatus.setFinalState(xmlStatusArray[i].getFinalState());
                        if (xmlStatusArray[i].isSetLockSpecimens())
                            newStatus.setSpecimensLocked(xmlStatusArray[i].getLockSpecimens());
                        SpecimenRequestManager.get().createRequestStatus(ctx.getUser(), newStatus);
                    }
                    else
                    {
                        ctx.getLogger().warn("Skipping request status that does not have a label.");
                    }
                }
            }
            if (xmlRequestStatuses.isSetMultipleSearch())
            {
                StatusSettings settings = SettingsManager.get().getStatusSettings(ctx.getContainer());
                if (settings.isUseShoppingCart() != xmlRequestStatuses.getMultipleSearch())
                {
                    settings.setUseShoppingCart(xmlRequestStatuses.getMultipleSearch());
                    SettingsManager.get().saveStatusSettings(ctx.getContainer(), settings);
                }
            }
        }
    }

    private void importRequestActors(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        SpecimenSettingsType.RequestActors xmlRequestActors = xmlSettings.getRequestActors();
        if (xmlRequestActors != null)
        {
            SpecimenSettingsType.RequestActors.Actor[] xmlActorArray = xmlRequestActors.getActorArray();
            if (xmlActorArray.length > 0)
            {
                // remove any existing not in-use actors
                // note: this will also remove all groups and members for that actor
                Set<Integer> inUseActorIds = SpecimenRequestRequirementProvider.get().getActorsInUseSet(ctx.getContainer());
                Map<String, SpecimenRequestActor> inUseActors = new HashMap<>();
                for (SpecimenRequestActor existingActor : SpecimenRequestRequirementProvider.get().getActors(ctx.getContainer()))
                {
                    if (!inUseActorIds.contains(existingActor.getRowId()))
                        existingActor.delete();
                    else
                        inUseActors.put(existingActor.getLabel(), existingActor);
                }

                // create new request actors from the xml settings file
                for (int i = 0; i < xmlActorArray.length; i++)
                {
                    String newActorLabel = xmlActorArray[i].isSetLabel() && xmlActorArray[i].getLabel() != null && !xmlActorArray[i].getLabel().isEmpty() ? xmlActorArray[i].getLabel() : null;
                    if (newActorLabel != null)
                    {
                        // create new request actor if label does not match existing in-use actor label
                        SpecimenRequestActor actor;
                        if (!inUseActors.containsKey(newActorLabel))
                        {
                            actor = new SpecimenRequestActor();
                            actor.setContainer(ctx.getContainer());
                            actor.setLabel(newActorLabel);
                            actor.setSortOrder(i);
                            actor.setPerSite(SpecimenSettingsType.RequestActors.Actor.Type.LOCATION.equals(xmlActorArray[i].getType())); // default is per study
                            actor.create(ctx.getUser());
                        }
                        else
                        {
                            actor = inUseActors.get(newActorLabel);
                        }

                        if (xmlActorArray[i].getGroups() != null)
                        {
                            for (GroupType newActorGroup : xmlActorArray[i].getGroups().getGroupArray())
                            {
                                // verify that the location exists in this study, for the per site actor type
                                LocationImpl location = null;
                                if (actor.isPerSite())
                                {
                                    location = LocationCache.getForLabel(ctx.getContainer(), newActorGroup.getName());

                                    if (location == null)
                                    {
                                        ctx.getLogger().warn("Request actor group not created for \"" + actor.getLabel()
                                                + ", " + newActorGroup.getName() + "\". Could not find matching study location.");
                                        continue;
                                    }
                                }

                                // note: currently, request actor groups only have users as members (no groups in groups)
                                Integer newGroupId = actor.getGroupId(location, true);
                                GroupManager.importGroupMembers(SecurityManager.getGroup(newGroupId), newActorGroup, ctx.getLogger(), ctx.getContainer());
                            }
                        }
                    }
                    else
                    {
                        ctx.getLogger().warn("Skipping request actor that does not have a label.");
                    }
                }
            }
        }
    }

    private void importDefaultRequirements(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        SpecimenSettingsType.DefaultRequirements xmlDefRequirements = xmlSettings.getDefaultRequirements();
        if (xmlDefRequirements != null)
        {
            // remove existing default requirements for this container, full replacement
            ManageReqsBean existingDefaultReqBeans = new ManageReqsBean(ctx.getUser(), ctx.getContainer());
            List<SpecimenRequestRequirement> existingDefaultReqs = new ArrayList<>();
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getOriginatorRequirements()));
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getProviderRequirements()));
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getReceiverRequirements()));
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getGeneralRequirements()));
            for (SpecimenRequestRequirement existingReq : existingDefaultReqs)
            {
                try
                {
                    SpecimenRequestManager.get().deleteRequestRequirement(ctx.getUser(), existingReq, false);
                }
                catch(AttachmentService.DuplicateFilenameException e)
                {} // no op, this would only occur with deleteRequestRequirement when createEvent is true
            }

            for (DefaultRequirementsType xmlReq : xmlDefRequirements.getOriginatingLabArray())
                createDefaultRequirement(xmlReq, ctx, SpecimenRequestRequirementType.ORIGINATING_SITE);
            for (DefaultRequirementsType xmlReq : xmlDefRequirements.getProvidingLabArray())
                createDefaultRequirement(xmlReq, ctx, SpecimenRequestRequirementType.PROVIDING_SITE);
            for (DefaultRequirementsType xmlReq : xmlDefRequirements.getReceivingLabArray())
                createDefaultRequirement(xmlReq, ctx, SpecimenRequestRequirementType.RECEIVING_SITE );
            for (DefaultRequirementsType xmlReq : xmlDefRequirements.getGeneralArray())
                createDefaultRequirement(xmlReq, ctx, SpecimenRequestRequirementType.NON_SITE_BASED);
        }
    }

    private void createDefaultRequirement(DefaultRequirementsType xmlReqs, SimpleStudyImportContext ctx, RequirementType type)
    {
        if (xmlReqs != null && xmlReqs.getRequirementArray().length > 0)
        {
            for (DefaultRequirementType xmlReq : xmlReqs.getRequirementArray())
            {
                List<SpecimenRequestActor> matchingActors = SpecimenRequestRequirementProvider.get().getActorsByLabel(ctx.getContainer(), xmlReq.getActor());
                if (matchingActors.size() > 0)
                {
                    SpecimenRequestRequirement requirement = new SpecimenRequestRequirement();
                    requirement.setContainer(ctx.getContainer());
                    requirement.setActorId(matchingActors.get(0).getRowId());
                    requirement.setDescription(xmlReq.getDescription());
                    requirement.setRequestId(-1);
                    SpecimenRequestRequirementProvider.get().createDefaultRequirement(ctx.getUser(), requirement, type);
                }
                else
                {
                    ctx.getLogger().warn("Could not find matching actor with label: " + xmlReq.getActor());
                }
            }
        }
    }

    private void importDisplaySettings(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        ctx.getLogger().info("Importing specimen display settings");
        SpecimenSettingsType.DisplaySettings xmlDisplay = xmlSettings.getDisplaySettings();
        if (xmlDisplay != null)
        {
            DisplaySettings display = new DisplaySettings();
            SpecimenSettingsType.DisplaySettings.CommentsAndQC commentsAndQC = xmlDisplay.getCommentsAndQC();
            if (commentsAndQC != null)
            {
                display.setDefaultToCommentsMode(commentsAndQC.getDefaultToCommentsMode());
                display.setEnableManualQCFlagging(commentsAndQC.getEnableManualQCFlagging());
            }
            SpecimenSettingsType.DisplaySettings.LowSpecimenWarnings warnings = xmlDisplay.getLowSpecimenWarnings();
            if (warnings != null)
            {
                display.setLastVial(warnings.getLastVial());
                display.setZeroVials(warnings.getZeroVials());
            }

            SettingsManager.get().saveDisplaySettings(ctx.getContainer(), display);
        }
    }

    private void importRequestForm(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings) throws SQLException
    {
        ctx.getLogger().info("Importing specimen request forms");
        // try to merge with any existing request forms, even though there doesn't seem to be the notion of a duplicate value
        Set<String> currentInputs = new HashSet<>();
        List<SpecimenRequestInput> inputs = new ArrayList<>();
        for (SpecimenRequestInput input : SpecimenRequestManager.get().getNewSpecimenRequestInputs(ctx.getContainer(), false))
        {
            inputs.add(input);
            currentInputs.add(input.getTitle());
        }

        SpecimenSettingsType.RequestForms xmlForms = xmlSettings.getRequestForms();
        if (xmlForms != null)
        {
            SpecimenSettingsType.RequestForms.Form[] formArray = xmlForms.getFormArray();
            if (formArray != null && formArray.length > 0)
            {
                for (SpecimenSettingsType.RequestForms.Form form : formArray)
                {
                    if (!currentInputs.contains(form.getTitle()))
                    {
                        SpecimenRequestInput input = new SpecimenRequestInput(
                            form.getTitle(),
                            form.getHelpText(),
                            form.getDisplayOrder(),
                            form.getMultiLine(),
                            form.getRequired(),
                            form.getRememberSiteValue()
                        );

                        inputs.add(input);
                    }
                    else
                        ctx.getLogger().info("There is currently a form with the same title: " + form.getTitle() + ", skipping this from import");
                }
                inputs.sort(Comparator.comparingInt(SpecimenRequestInput::getDisplayOrder));
                SpecimenRequestManager.get().saveNewSpecimenRequestInputs(ctx.getContainer(), inputs.toArray(new SpecimenRequestInput[inputs.size()]));
            }
        }
    }

    private void importNotifications(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        ctx.getLogger().info("Importing specimen notification settings");
        SpecimenSettingsType.Notifications xmlNotifications = xmlSettings.getNotifications();
        if (xmlNotifications != null)
        {
            RequestNotificationSettings notifications = new RequestNotificationSettings();

            if (xmlNotifications.getReplyTo() != null)
                notifications.setReplyTo(xmlNotifications.getReplyTo());
            if (xmlNotifications.getSubjectSuffix() != null)
                notifications.setSubjectSuffix(xmlNotifications.getSubjectSuffix());
            notifications.setNewRequestNotifyCheckbox(xmlNotifications.getNewRequestNotifyCheckbox());
            if (xmlNotifications.getNewRequestNotify() != null)
                notifications.setNewRequestNotify(xmlNotifications.getNewRequestNotify());
            notifications.setCcCheckbox(xmlNotifications.getCcCheckbox());
            if (xmlNotifications.getCc() != null)
                notifications.setCc(xmlNotifications.getCc());
            if (xmlNotifications.getDefaultEmailNotify() != null)
                notifications.setDefaultEmailNotify(xmlNotifications.getDefaultEmailNotify());
            if (xmlNotifications.getSpecimensAttachment() != null)
                notifications.setSpecimensAttachment(xmlNotifications.getSpecimensAttachment());

            SettingsManager.get().saveRequestNotificationSettings(ctx.getContainer(), notifications);
        }
    }

    private void importRequestabilityRules(SimpleStudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        ctx.getLogger().info("Importing specimen requestability rules");
        SpecimenSettingsType.RequestabilityRules xmlRules = xmlSettings.getRequestabilityRules();
        if (xmlRules != null)
        {
            List<RequestabilityManager.RequestableRule> rules = new ArrayList<>();
            SpecimenSettingsType.RequestabilityRules.Rule[] xmlRuleArray = xmlRules.getRuleArray();
            if (xmlRuleArray != null && xmlRuleArray.length > 0)
            {
                for (SpecimenSettingsType.RequestabilityRules.Rule rule : xmlRuleArray)
                {
                    RequestabilityManager.RuleType type = RequestabilityManager.RuleType.valueOf(rule.getType());
                    rules.add(type.createRule(ctx.getContainer(), rule.getRuleData()));
                }
                RequestabilityManager.getInstance().saveRules(ctx.getContainer(), ctx.getUser(), rules);
            }
        }
    }
}
