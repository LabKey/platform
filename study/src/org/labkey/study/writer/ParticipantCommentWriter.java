/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;
import org.labkey.api.writer.VirtualFile;

/**
 * User: klum
 * Date: Sep 30, 2009
 */
public class ParticipantCommentWriter implements InternalStudyWriter
{
    public String getDataType()
    {
        return StudyArchiveDataTypes.PARTICIPANT_COMMENTS;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Comments comment = studyXml.addNewComments();

        if (study.getParticipantCommentDatasetId() != null && study.getParticipantCommentDatasetId() != -1)
        {
            comment.setParticipantCommentDatasetId(study.getParticipantCommentDatasetId());
            comment.setParticipantCommentDatasetProperty(study.getParticipantCommentProperty());
        }

        if (study.getParticipantVisitCommentDatasetId() != null && study.getParticipantVisitCommentDatasetId() != -1)
        {
            comment.setParticipantVisitCommentDatasetId(study.getParticipantVisitCommentDatasetId());
            comment.setParticipantVisitCommentDatasetProperty(study.getParticipantVisitCommentProperty());
        }
    }
}
