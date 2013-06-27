/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.security.xml.GroupType;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.SampleRequestActor;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.SampleRequestStatus;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.requirements.RequirementType;
import org.labkey.study.requirements.SpecimenRequestRequirementType;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.samples.settings.StatusSettings;
import org.labkey.study.xml.DefaultRequirementType;
import org.labkey.study.xml.DefaultRequirementsType;
import org.labkey.study.xml.LegacySpecimenSettingsType;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 6/13/13
 */
public class SpecimenSettingsImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "specimen settings";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile studyDir, BindException errors) throws SQLException, ImportException, IOException
    {
        StudyDocument.Study.Specimens xmlSettings = ctx.getXml().getSpecimens();
        if (xmlSettings != null)
        {
            ctx.getLogger().info("Loading specimen settings");

            StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer()).createMutable();

            if (xmlSettings.getSettings() != null)
            {
                // Import specimen settings from specimen_settings.xml doc for versions >= 13.2.
                VirtualFile settingsDir = studyDir;
                if (xmlSettings.getDir() != null)
                    settingsDir = studyDir.getDir(xmlSettings.getDir());

                SpecimensDocument specimensDoc = (SpecimensDocument)settingsDir.getXmlBean(xmlSettings.getSettings());
                SpecimenSettingsType xmlSpecimens = specimensDoc.getSpecimens();

                importSettings(study, ctx, xmlSpecimens);
            }
            else
            {
                // Import specimen settings from study.xml doc for versions <13.2.
                importLegacySettings(study, ctx, xmlSettings);
            }

            StudyManager.getInstance().updateStudy(ctx.getUser(), study);
        }
    }

    // Import specimen settings for versions >= 13.2.
    private static void importSettings(StudyImpl study, StudyImportContext ctx, SpecimenSettingsType xmlSettings) throws SQLException
    {
        Container c = ctx.getContainer();
        RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(c);

        // webpart groupings
        SpecimenSettingsType.WebPartGroupings xmlWebPartGroupings = xmlSettings.getWebPartGroupings();
        if (null != xmlWebPartGroupings)
        {
            SpecimenSettingsType.WebPartGroupings.Grouping[] xmlGroupings = xmlWebPartGroupings.getGroupingArray();
            if (null != xmlGroupings)
            {
                ArrayList<String[]> groupings = new ArrayList<>(2);
                for (int i = 0; i < xmlGroupings.length; i++)
                {
                    String[] groupBys = xmlGroupings[i].getGroupByArray();
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
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);

        // location types
        SpecimenSettingsType.LocationTypes xmlLocationTypes = xmlSettings.getLocationTypes();
        if (null != xmlLocationTypes)
        {
            if (xmlLocationTypes.isSetRepository() && xmlLocationTypes.getRepository().isSetAllowRequests())
                study.setAllowReqLocRepository(xmlLocationTypes.getRepository().getAllowRequests());
            if (xmlLocationTypes.isSetClinic() && xmlLocationTypes.getClinic().isSetAllowRequests())
                study.setAllowReqLocClinic(xmlLocationTypes.getClinic().getAllowRequests());
            if (xmlLocationTypes.isSetSiteAffiliatedLab() && xmlLocationTypes.getSiteAffiliatedLab().isSetAllowRequests())
                study.setAllowReqLocSal(xmlLocationTypes.getSiteAffiliatedLab().getAllowRequests());
            if (xmlLocationTypes.isSetEndpointLab() && xmlLocationTypes.getEndpointLab().isSetAllowRequests())
                study.setAllowReqLocEndpoint(xmlLocationTypes.getEndpointLab().getAllowRequests());
        }

        importRequestStatuses(study, ctx, xmlSettings);
        importRequestActors(study, ctx, xmlSettings);
        importDefaultRequirements(study, ctx, xmlSettings);
    }



    private static void importRequestStatuses(StudyImpl study, StudyImportContext ctx, SpecimenSettingsType xmlSettings) throws SQLException
    {
        SpecimenSettingsType.RequestStatuses xmlRequestStatuses = xmlSettings.getRequestStatuses();
        if (xmlRequestStatuses != null)
        {
            SpecimenSettingsType.RequestStatuses.Status[] xmlStatusArray = xmlRequestStatuses.getStatusArray();
            if (xmlStatusArray.length > 0)
            {
                // remove any existing not in-use, non-system statuses for this container before importing the new ones
                Set<Integer> inUseStatusIds = study.getSampleRequestStatusesInUse();
                List<String> inUseStatusLabels = new ArrayList<>();
                for (SampleRequestStatus existingStatus : study.getSampleRequestStatuses(ctx.getUser()))
                {
                    if (!existingStatus.isSystemStatus() && !inUseStatusIds.contains(existingStatus.getRowId()))
                        SampleManager.getInstance().deleteRequestStatus(ctx.getUser(), existingStatus);
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
                        SampleRequestStatus newStatus = new SampleRequestStatus();
                        newStatus.setContainer(ctx.getContainer());
                        newStatus.setLabel(newStatusLabel);
                        newStatus.setSortOrder(i+1);
                        if (xmlStatusArray[i].isSetFinalState())
                            newStatus.setFinalState(xmlStatusArray[i].getFinalState());
                        if (xmlStatusArray[i].isSetLockSpecimens())
                            newStatus.setSpecimensLocked(xmlStatusArray[i].getLockSpecimens());
                        SampleManager.getInstance().createRequestStatus(ctx.getUser(), newStatus);
                    }
                    else
                    {
                        ctx.getLogger().warn("Skipping request status that does not have a label.");
                    }
                }
            }
            if (xmlRequestStatuses.isSetMultipleSearch())
            {
                StatusSettings settings = SampleManager.getInstance().getStatusSettings(ctx.getContainer());
                if (settings.isUseShoppingCart() != xmlRequestStatuses.getMultipleSearch())
                {
                    settings.setUseShoppingCart(xmlRequestStatuses.getMultipleSearch());
                    SampleManager.getInstance().saveStatusSettings(ctx.getContainer(), settings);
                }
            }
        }
    }

    private static void importRequestActors(StudyImpl study, StudyImportContext ctx, SpecimenSettingsType xmlSettings)
    {
        SpecimenSettingsType.RequestActors xmlRequestActors = xmlSettings.getRequestActors();
        if (xmlRequestActors != null)
        {
            SpecimenSettingsType.RequestActors.Actor[] xmlActorArray = xmlRequestActors.getActorArray();
            if (xmlActorArray.length > 0)
            {
                // remove any existing not in-use actors
                // note: this will also remove all groups and members for that actor
                Set<Integer> inUseActorIds = study.getSampleRequestActorsInUse();
                Map<String, SampleRequestActor> inUseActors = new HashMap<>();
                for (SampleRequestActor existingActor : study.getSampleRequestActors())
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
                        SampleRequestActor actor;
                        if (!inUseActors.keySet().contains(newActorLabel))
                        {
                            actor = new SampleRequestActor();
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
                                    List<LocationImpl> matchingLocs = StudyManager.getInstance().getLocationsByLabel(ctx.getContainer(), newActorGroup.getName());
                                    if (matchingLocs.size() > 0)
                                        location = matchingLocs.get(0);

                                    if (location == null)
                                    {
                                        ctx.getLogger().warn("Request actor group not created for \"" + actor.getLabel()
                                                + ", " + newActorGroup.getName() + "\". Could not find matching study location.");
                                        continue;
                                    }
                                }

                                // note: currently, request actor groups only have users as membesr (no groups in groups)
                                Integer newGroupId = actor.getGroupId(location, true);
                                GroupManager.importGroupMembers(SecurityManager.getGroup(newGroupId), newActorGroup, ctx.getLogger());
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

    private static void importDefaultRequirements(StudyImpl study, StudyImportContext ctx, SpecimenSettingsType xmlSettings) throws SQLException
    {
        SpecimenSettingsType.DefaultRequirements xmlDefRequirements = xmlSettings.getDefaultRequirements();
        if (xmlDefRequirements != null)
        {
            // remove existing default requirements for this container, full replacement
            SpecimenController.ManageReqsBean existingDefaultReqBeans = new SpecimenController.ManageReqsBean(ctx.getUser(), study.getContainer());
            List<SampleRequestRequirement> existingDefaultReqs = new ArrayList<>();
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getOriginatorRequirements()));
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getProviderRequirements()));
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getReceiverRequirements()));
            existingDefaultReqs.addAll(Arrays.asList(existingDefaultReqBeans.getGeneralRequirements()));
            for (SampleRequestRequirement existingReq : existingDefaultReqs)
            {
                try
                {
                    SampleManager.getInstance().deleteRequestRequirement(ctx.getUser(), existingReq, false);
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

    private static void createDefaultRequirement(DefaultRequirementsType xmlReqs, StudyImportContext ctx, RequirementType type)
    {
        if (xmlReqs != null && xmlReqs.getRequirementArray().length > 0)
        {
            for (DefaultRequirementType xmlReq : xmlReqs.getRequirementArray())
            {
                List<SampleRequestActor> matchingActors = SampleManager.getInstance().getRequirementsProvider().getActorsByLabel(ctx.getContainer(), xmlReq.getActor());
                if (matchingActors.size() > 0)
                {
                    SampleRequestRequirement requirement = new SampleRequestRequirement();
                    requirement.setContainer(ctx.getContainer());
                    requirement.setActorId(matchingActors.get(0).getRowId());
                    requirement.setDescription(xmlReq.getDescription());
                    requirement.setRequestId(-1);
                    SampleManager.getInstance().getRequirementsProvider().createDefaultRequirement(ctx.getUser(), requirement, type);
                }
                else
                {
                    ctx.getLogger().warn("Could not find matching actor with label: " + xmlReq.getActor());
                }
            }
        }
    }

    // Import specimen settings from study.xml doc for versions <13.2.
    private static void importLegacySettings(StudyImpl study, StudyImportContext ctx, LegacySpecimenSettingsType xmlSettings)
    {
        Container c = ctx.getContainer();
        RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(c);

        // webpart groupings
        StudyDocument.Study.Specimens.SpecimenWebPartGroupings xmlSpecimenWebPartGroupings = xmlSettings.getSpecimenWebPartGroupings();
        if (null != xmlSpecimenWebPartGroupings)
        {
            StudyDocument.Study.Specimens.SpecimenWebPartGroupings.Grouping[] xmlGroupings = xmlSpecimenWebPartGroupings.getGroupingArray();
            if (null != xmlGroupings)
            {
                ArrayList<String[]> groupings = new ArrayList<>(2);
                for (int i = 0; i < xmlGroupings.length; i += 1)
                {
                    String[] groupBys = xmlGroupings[i].getGroupByArray();
                    groupings.add(groupBys);
                }
                reposSettings.setSpecimenWebPartGroupings(groupings);
            }
        }

        // repository type
        SpecimenRepositoryType.Enum repositoryType = xmlSettings.getRepositoryType();
        boolean simple = (SpecimenRepositoryType.STANDARD == repositoryType);
        reposSettings.setSimple(simple);
        reposSettings.setEnableRequests(!simple);
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);

        // location types
        if (xmlSettings.isSetAllowReqLocRepository())
            study.setAllowReqLocRepository(xmlSettings.getAllowReqLocRepository());
        if (xmlSettings.isSetAllowReqLocClinic())
            study.setAllowReqLocClinic(xmlSettings.getAllowReqLocClinic());
        if (xmlSettings.isSetAllowReqLocSal())
            study.setAllowReqLocSal(xmlSettings.getAllowReqLocSal());
        if (xmlSettings.isSetAllowReqLocEndpoint())
            study.setAllowReqLocEndpoint(xmlSettings.getAllowReqLocEndpoint());
    }
}
