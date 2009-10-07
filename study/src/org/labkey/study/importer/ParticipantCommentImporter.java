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

package org.labkey.study.importer;

import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 30, 2009
 */
public class ParticipantCommentImporter implements InternalStudyImporter
{
    public String getDescription()
    {
        return "Participant Comment Settings";
    }

    public void process(StudyImpl study, ImportContext ctx, File root) throws Exception
    {
        StudyDocument.Study.Comments commentsXml = ctx.getStudyXml().getComments();

        if (commentsXml != null)
        {
            if (commentsXml.isSetParticipantCommentDatasetId())
            {
                study.setParticipantCommentDataSetId(commentsXml.getParticipantCommentDatasetId());
                study.setParticipantCommentProperty(commentsXml.getParticipantCommentDatasetProperty());
            }

            if (commentsXml.isSetParticipantVisitCommentDatasetId())
            {
                study.setParticipantVisitCommentDataSetId(commentsXml.getParticipantVisitCommentDatasetId());
                study.setParticipantVisitCommentProperty(commentsXml.getParticipantVisitCommentDatasetProperty());
            }
        }
    }
}
