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
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.SampleManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.xml.LegacySpecimenSettingsType;
import org.labkey.study.xml.SpecimenRepositoryType;
import org.labkey.study.xml.SpecimenSettingsType;
import org.labkey.study.xml.SpecimensDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * User: kevink
 * Date: 6/13/13
 */
public class SpecimenSettingsImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "Specimen Settings Importer";
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
    private static void importSettings(StudyImpl study, StudyImportContext ctx, SpecimenSettingsType xmlSettings)
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
        reposSettings.setEnableRequests(!simple);
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);

        // location types
        SpecimenSettingsType.LocationTypes xmlLocationTypes = xmlSettings.getLocationTypes();
        if (xmlLocationTypes.isSetRepository() && xmlLocationTypes.getRepository().isSetAllowRequests())
            study.setAllowReqLocClinic(xmlLocationTypes.getRepository().getAllowRequests());
        if (xmlLocationTypes.isSetClinic() && xmlLocationTypes.getClinic().isSetAllowRequests())
            study.setAllowReqLocClinic(xmlLocationTypes.getClinic().getAllowRequests());
        if (xmlLocationTypes.isSetSiteAffiliatedLab() && xmlLocationTypes.getSiteAffiliatedLab().isSetAllowRequests())
            study.setAllowReqLocClinic(xmlLocationTypes.getSiteAffiliatedLab().getAllowRequests());
        if (xmlLocationTypes.isSetEndpointLab() && xmlLocationTypes.getEndpointLab().isSetAllowRequests())
            study.setAllowReqLocClinic(xmlLocationTypes.getEndpointLab().getAllowRequests());

        // UNDONE: import other settings...
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
