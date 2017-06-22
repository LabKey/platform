/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: Sep 27, 2011
 * Time: 1:36:40 PM
 */
public class ProtocolDocumentImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "protocol documents";
    }

    @Override
    public String getDataType() { return StudyArchiveDataTypes.PROTOCOL_DOCUMENTS; }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            StudyImpl study = ctx.getStudy();
            ExportDirType protocolXml = ctx.getXml().getProtocolDocs();

            ctx.getLogger().info("Loading " + getDescription());

            VirtualFile folder = root.getDir(protocolXml.getDir());
            List<AttachmentFile> attachments = new ArrayList<>();
            List<String> existing = new ArrayList<>();

            for (Attachment attachment : study.getProtocolDocuments())
                existing.add(attachment.getName());

            for (String fileName : folder.list())
            {
                ctx.getLogger().info("importing protocol document: " + fileName);

                if (existing.contains(fileName))
                    study.removeProtocolDocument(fileName, ctx.getUser());
                
                attachments.add(new InputStreamAttachmentFile(folder.getInputStream(fileName), fileName));
            }

            study.attachProtocolDocument(attachments, ctx.getUser());

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getProtocolDocs() != null;
    }
}
