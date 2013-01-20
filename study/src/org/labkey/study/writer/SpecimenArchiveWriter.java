/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.writer.StandardSpecimenWriter.QueryInfo;
import org.labkey.study.xml.RepositoryType;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:28:37 AM
 */
class SpecimenArchiveWriter implements InternalStudyWriter
{
    private static final String DEFAULT_DIRECTORY = "specimens";
    public static final String SELECTION_TEXT = "Specimens";

    public String getSelectionText()
    {
        return SELECTION_TEXT;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Specimens specimens = studyXml.addNewSpecimens();
        RepositorySettings repositorySettings = study.getRepositorySettings();
        specimens.setRepositoryType(repositorySettings.isSimple() ? RepositoryType.STANDARD : RepositoryType.ADVANCED);
        specimens.setDir(DEFAULT_DIRECTORY);
        specimens.setAllowReqLocRepository(study.isAllowReqLocRepository());
        specimens.setAllowReqLocClinic(study.isAllowReqLocClinic());
        specimens.setAllowReqLocSal(study.isAllowReqLocSal());
        specimens.setAllowReqLocEndpoint(study.isAllowReqLocEndpoint());
        ArrayList<String[]> groupings = repositorySettings.getSpecimenWebPartGroupings();
        if (groupings.size() > 0)
        {
            StudyDocument.Study.Specimens.SpecimenWebPartGroupings specimenWebPartGroupings = specimens.addNewSpecimenWebPartGroupings();
            for (int i = 0; i < 1 /*groupings.size()*/; i += 1)
            {
                StudyDocument.Study.Specimens.SpecimenWebPartGroupings.Grouping specimenWebPartGrouping = specimenWebPartGroupings.addNewGrouping();
                specimenWebPartGrouping.setGroupByArray(groupings.get(i));
            }
        }
        String archiveName = vf.makeLegalName(study.getLabel().replaceAll("\\s", "") + ".specimens");
        VirtualFile zip = vf.createZipArchive(archiveName);
        if (!zip.equals(vf)) // MemoryVirtualFile doesn't add a zip archive, it just returns vf
            specimens.setFile(archiveName);

        StudySchema schema = StudySchema.getInstance();

        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoSite(), "labs", SpecimenImporter.SITE_COLUMNS), ctx, zip);
        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoPrimaryType(), "primary_types", SpecimenImporter.PRIMARYTYPE_COLUMNS), ctx, zip);
        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoAdditiveType(), "additives", SpecimenImporter.ADDITIVE_COLUMNS), ctx, zip);
        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoDerivativeType(), "derivatives", SpecimenImporter.DERIVATIVE_COLUMNS), ctx, zip);

        new SpecimenWriter().write(study, ctx, zip);

        zip.close();
    }
}
