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
package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.security.xml.GroupType;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.SampleRequestActor;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.SampleRequestStatus;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.samples.settings.StatusSettings;
import org.labkey.study.xml.DefaultRequirementType;
import org.labkey.study.xml.DefaultRequirementsType;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;
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
    public String getSelectionText()
    {
        return "Specimen Settings";
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

        RepositorySettings repositorySettings = study.getRepositorySettings();
        xmlSettings.setRepositoryType(repositorySettings.isSimple() ? SpecimenRepositoryType.STANDARD : SpecimenRepositoryType.ADVANCED);
        xmlSettings.setEnableRequests(repositorySettings.isEnableRequests());
        xmlSettings.setEditableRepository(repositorySettings.isSpecimenDataEditable());

        // specimen location types
        SpecimenSettingsType.LocationTypes xmlLocationTypes = xmlSettings.addNewLocationTypes();
        xmlLocationTypes.addNewRepository().setAllowRequests(study.isAllowReqLocRepository());
        xmlLocationTypes.addNewClinic().setAllowRequests(study.isAllowReqLocClinic());
        xmlLocationTypes.addNewSiteAffiliatedLab().setAllowRequests(study.isAllowReqLocSal());
        xmlLocationTypes.addNewEndpointLab().setAllowRequests(study.isAllowReqLocEndpoint());

        // specimen webpart groupings
        ArrayList<String[]> groupings = repositorySettings.getSpecimenWebPartGroupings();
        if (groupings.size() > 0)
        {
            SpecimenSettingsType.WebPartGroupings xmlWebPartGroupings = xmlSettings.addNewWebPartGroupings();
            for (String[] grouping : groupings)
            {
                SpecimenSettingsType.WebPartGroupings.Grouping xmlGrouping = xmlWebPartGroupings.addNewGrouping();
                xmlGrouping.setGroupByArray(grouping);
            }
        }

        // these settigns only apply if repository type is Advanced and specimen requests are enabled
        if (!repositorySettings.isSimple() && repositorySettings.isEnableRequests())
        {
            // request statuses
            SpecimenSettingsType.RequestStatuses xmlRequestStatuses = xmlSettings.addNewRequestStatuses();
            List<SampleRequestStatus> statuses = study.getSampleRequestStatuses(ctx.getUser());
            if (statuses.size() > 0)
            {
                for (SampleRequestStatus status : statuses)
                {
                    if (!status.isSystemStatus()) // don't export system statuses
                    {
                        SpecimenSettingsType.RequestStatuses.Status xmlStatus = xmlRequestStatuses.addNewStatus();
                        xmlStatus.setLabel(status.getLabel());
                        xmlStatus.setFinalState(status.isFinalState());
                        xmlStatus.setLockSpecimens(status.isSpecimensLocked());
                    }
                }
            }
            StatusSettings statusSettings = SampleManager.getInstance().getStatusSettings(study.getContainer());
            if (!statusSettings.isUseShoppingCart()) // default is to use shopping cart
                xmlRequestStatuses.setMultipleSearch(statusSettings.isUseShoppingCart());

            // request actors
            SampleRequestActor[] actors = study.getSampleRequestActors();
            if (actors != null && actors.length > 0)
            {
                SpecimenSettingsType.RequestActors xmlRequestActors = xmlSettings.addNewRequestActors();
                for (SampleRequestActor actor : actors)
                {
                    SpecimenSettingsType.RequestActors.Actor xmlActor = xmlRequestActors.addNewActor();
                    xmlActor.setLabel(actor.getLabel());
                    xmlActor.setType(actor.isPerSite() ? SpecimenSettingsType.RequestActors.Actor.Type.LOCATION : SpecimenSettingsType.RequestActors.Actor.Type.STUDY);

                    SpecimenSettingsType.RequestActors.Actor.Groups xmlGroups = xmlActor.addNewGroups();
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

            // default requirements
            SpecimenController.ManageReqsBean defRequirments = new SpecimenController.ManageReqsBean(ctx.getUser(), study.getContainer());
            SpecimenSettingsType.DefaultRequirements xmlDefRequirements = xmlSettings.addNewDefaultRequirements();
            if (defRequirments.getOriginatorRequirements().length > 0)
            {
                DefaultRequirementsType xmlOrigLabReq = xmlDefRequirements.addNewOriginatingLab();
                for (SampleRequestRequirement req : defRequirments.getOriginatorRequirements())
                    writeDefaultRequirement(xmlOrigLabReq, req);
            }
            if (defRequirments.getProviderRequirements().length > 0)
            {
                DefaultRequirementsType xmlProviderReq = xmlDefRequirements.addNewProvidingLab();
                for (SampleRequestRequirement req : defRequirments.getProviderRequirements())
                    writeDefaultRequirement(xmlProviderReq, req);
            }
            if (defRequirments.getReceiverRequirements().length > 0)
            {
                DefaultRequirementsType xmlReceiverReq = xmlDefRequirements.addNewReceivingLab();
                for (SampleRequestRequirement req : defRequirments.getReceiverRequirements())
                    writeDefaultRequirement(xmlReceiverReq, req);
            }
            if (defRequirments.getGeneralRequirements().length > 0)
            {
                DefaultRequirementsType xmlGeneralReq = xmlDefRequirements.addNewGeneral();
                for (SampleRequestRequirement req : defRequirments.getGeneralRequirements())
                    writeDefaultRequirement(xmlGeneralReq, req);
            }
        }

        // write out the xml
        dir.saveXmlBean(DEFAULT_SETTINGS_FILE, xmlSettingsDoc);
    }

    private void writeDefaultRequirement(DefaultRequirementsType xmlReqType, SampleRequestRequirement req)
    {
        DefaultRequirementType xmlReq = xmlReqType.addNewRequirement();
        xmlReq.setActor(req.getActor().getLabel());
        xmlReq.setDescription(req.getDescription());
    }

    private void writeActorGroup(SampleRequestActor actor, @Nullable LocationImpl location, GroupType xmlGroup)
    {
        // for a actor type of per location, use the location label as the group name
        // otherwise use the actor label in the per study case
        xmlGroup.setName(location != null ? location.getLabel() : actor.getLabel());

        // these actor groups only currently have Users (no groups within groups)
        User[] members = actor.getMembers(location);
        GroupType.Users xmlUsers = xmlGroup.addNewUsers();
        for (User member : members)
        {
            xmlUsers.addNewUser().setName(member.getEmail());
        }
        // TODO: move to a centralized writeGroupMembers location?
    }
}
