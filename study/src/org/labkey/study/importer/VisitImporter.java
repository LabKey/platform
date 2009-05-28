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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.model.StudyImpl;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: May 17, 2009
 * Time: 8:11:51 AM
 */
public class VisitImporter
{
    boolean process(StudyImpl study, ImportContext ctx, File root, BindException errors) throws IOException, SQLException
    {
        // Visit map
        StudyDocument.Study.Visits visitsXml = ctx.getStudyXml().getVisits();

        if (null != visitsXml)
        {
            File visitMap = new File(root, visitsXml.getFile());

            if (visitMap.exists())
            {
                String content = PageFlowUtil.getFileContentsAsString(visitMap);

                VisitMapImporter importer = new VisitMapImporter();
                List<String> errorMsg = new LinkedList<String>();

                if (!importer.process(ctx.getUser(), study, content, VisitMapImporter.Format.getFormat(visitMap), errorMsg))
                {
                    for (String error : errorMsg)
                        errors.reject("uploadVisitMap", error);

                    return false;
                }
            }
        }

        return true;
    }
}
