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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.study.ExternalStudyImporter;
import org.labkey.api.study.ExternalStudyImporterFactory;
import org.labkey.api.study.StudyContext;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class StudyListImporter implements ExternalStudyImporter
{
    public String getDescription()
    {
        return "lists";
    }

    public void process(StudyContext ctx, File root) throws Exception
    {
        StudyDocument.Study.Lists listsXml = ctx.getStudyXml().getLists();

        if (null != listsXml)
        {
            File listsDir = ctx.getStudyDir(root, listsXml.getDir());
            ListImporter importer = new ListImporter();
            Logger log = ctx.getLogger();
            List<String> errors = new LinkedList<String>();
            importer.process(root, listsDir, ctx.getContainer(), ctx.getUser(), errors, log);

            for (String error : errors)
                log.error(error);
        }
    }

    public Collection<PipelineJobWarning> postProcess(StudyContext ctx, File root) throws Exception
    {
        //nothing for now
        return null;
    }

    public static class Factory implements ExternalStudyImporterFactory
    {
        public ExternalStudyImporter create()
        {
            return new StudyListImporter();
        }
    }
}