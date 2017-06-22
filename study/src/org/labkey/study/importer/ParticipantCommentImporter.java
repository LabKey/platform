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

import org.labkey.api.admin.ImportException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

/**
 * User: klum
 * Date: Sep 30, 2009
 */
public class ParticipantCommentImporter implements InternalStudyImporter
{
    public String getDescription()
    {
        return StudyArchiveDataTypes.PARTICIPANT_COMMENTS;
    }

    public String getDataType() { return StudyArchiveDataTypes.PARTICIPANT_COMMENTS; }

    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            StudyImpl study = ctx.getStudy();
            StudyDocument.Study.Comments commentsXml = ctx.getXml().getComments();

            ctx.getLogger().info("Loading " + getDescription());

            if (commentsXml.isSetParticipantCommentDatasetId())
            {
                study.setParticipantCommentDatasetId(commentsXml.getParticipantCommentDatasetId());
                study.setParticipantCommentProperty(commentsXml.getParticipantCommentDatasetProperty());
            }

            if (commentsXml.isSetParticipantVisitCommentDatasetId())
            {
                study.setParticipantVisitCommentDatasetId(commentsXml.getParticipantVisitCommentDatasetId());
                study.setParticipantVisitCommentProperty(commentsXml.getParticipantVisitCommentDatasetProperty());
            }

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getComments() != null;
    }
}
