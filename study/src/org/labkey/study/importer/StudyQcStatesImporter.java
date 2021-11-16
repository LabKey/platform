/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.qc.export.AbstractDataStateImporter;
import org.labkey.api.qc.export.DataStateImportExportHelper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.qc.StudyQCImportExportHelper;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.qcStates.StudyqcDocument;
import org.springframework.validation.BindException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:36:25 PM
 *
 * Importer for dataset QC information located in the legacy study folder. Newer archive formats will write this out
 * at the folder level but this code is needed for backwards compatibility for archive formats older than 19.2
 */
public class StudyQcStatesImporter extends AbstractDataStateImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "QC States Importer";
    }

    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.QC_STATE_SETTINGS;
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            StudyDocument.Study.QcStates qcStates = ctx.getXml().getQcStates();
            DataStateImportExportHelper helper = new StudyQCImportExportHelper();

            ctx.getLogger().info("Loading QC states");
            StudyqcDocument doc = getSettingsFile(ctx, root);

            // if the import provides a study qc document (new in 13.3), parse it for the qc states, else
            // revert back to the previous behavior where we just set the default data visibility
            if (doc != null)
            {
                importQCStates(ctx, doc, helper);
            }
            else
            {
                helper.setShowPrivateDataByDefault(ctx.getContainer(), ctx.getUser(), qcStates.getShowPrivateDataByDefault());
            }

            ctx.getLogger().info("Done importing QC states");
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getQcStates() != null;
    }

    @Nullable
    private StudyqcDocument getSettingsFile(StudyImportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.QcStates qcXml  = ctx.getXml().getQcStates();

        if (qcXml != null)
        {
            String fileName = qcXml.getFile();

            if (fileName != null)
            {
                XmlObject doc = root.getXmlBean(fileName);
                if (doc instanceof StudyqcDocument)
                    return (StudyqcDocument)doc;
            }
        }
        return null;
    }
}
