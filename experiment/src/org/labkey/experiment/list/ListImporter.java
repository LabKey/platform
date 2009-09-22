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

package org.labkey.experiment.list;

import org.labkey.api.study.ExternalStudyImporter;
import org.labkey.api.study.ExternalStudyImporterFactory;
import org.labkey.api.study.StudyContext;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.FilenameFilter;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class ListImporter implements ExternalStudyImporter
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
            File listsDir = ctx.getStudyDir(root, listsXml.getDir(), "Study.xml");

            File[] listFiles = listsDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".tsv");
                }
            });

            ctx.getLogger().info(listFiles.length + " list" + (1 == listFiles.length ? "" : "s") + " imported (well, I'm thinking about it, anyway)");
        }
    }

    public void postProcess(StudyContext ctx, File root) throws Exception
    {
        //nothing for now
    }

    public static class Factory implements ExternalStudyImporterFactory
    {
        public ExternalStudyImporter create()
        {
            return new ListImporter();
        }
    }
}
