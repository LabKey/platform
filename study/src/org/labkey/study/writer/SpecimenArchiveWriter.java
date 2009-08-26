/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.writer.Archive;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.writer.StandardSpecimenWriter.QueryInfo;
import org.labkey.study.xml.RepositoryType;
import org.labkey.study.xml.StudyDocument;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:28:37 AM
 */
public class SpecimenArchiveWriter implements InternalStudyWriter
{
    private static final String DEFAULT_DIRECTORY = "specimens";

    public String getSelectionText()
    {
        return "Specimens";
    }

    public void write(StudyImpl study, StudyExportContextImpl ctx, VirtualFile root) throws Exception
    {
        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

        String archiveName = vf.makeLegalName(study.getLabel().replaceAll("\\s", "") + ".specimens");

        StudyDocument.Study studyXml = ctx.getStudyXml();
        StudyDocument.Study.Specimens specimens = studyXml.addNewSpecimens();
        specimens.setRepositoryType(study.getRepositorySettings().isSimple() ? RepositoryType.STANDARD : RepositoryType.ADVANCED);
        specimens.setDir(DEFAULT_DIRECTORY);
        specimens.setFile(archiveName);

        Archive zip = vf.createZipArchive(archiveName);

        StudySchema schema = StudySchema.getInstance();

        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoSite(), "labs", SpecimenImporter.SITE_COLUMNS), ctx, zip);
        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoPrimaryType(), "primary_types", SpecimenImporter.PRIMARYTYPE_COLUMNS), ctx, zip);
        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoAdditiveType(), "additives", SpecimenImporter.ADDITIVE_COLUMNS), ctx, zip);
        new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoDerivativeType(), "derivatives", SpecimenImporter.DERIVATIVE_COLUMNS), ctx, zip);

        new SpecimenWriter().write(study, ctx, zip);

        zip.close();
    }
}
