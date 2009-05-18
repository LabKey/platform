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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;
import org.labkey.study.writer.QueryWriter;
import org.labkey.study.xml.StudyDocument;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:21:56 PM
 */
public class QueryImporter
{
    private static final Logger _log = Logger.getLogger(QueryImporter.class);

    void process(ImportContext ctx, File root) throws ServletException, XmlException, IOException
    {
        StudyDocument.Study.Queries queriesXml = ctx.getStudyXml().getQueries();

        if (null != queriesXml)
        {
            File queriesDir = new File(root, queriesXml.getDir());

            File[] sqlFiles = queriesDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(QueryWriter.FILE_EXTENSION);
                }
            });

            File[] metaFileArray = queriesDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(QueryWriter.META_FILE_EXTENSION);
                }
            });

            Map<String, File> metaFiles = new HashMap<String, File>(metaFileArray.length);

            for (File metaFile : metaFileArray)
                metaFiles.put(metaFile.getName(), metaFile);

            for (File sqlFile : sqlFiles)
            {
                String metaFileName = sqlFile.getName().substring(0, sqlFile.getName().length() - QueryWriter.FILE_EXTENSION.length()) + QueryWriter.META_FILE_EXTENSION;
                File metaFile = metaFiles.get(metaFileName);

                if (null == metaFile)
                    throw new ServletException("QueryImport: SQL file \"" + sqlFile.getName() + "\" has no corresponding meta data file.");

                String sql = PageFlowUtil.getFileContentsAsString(sqlFile);
                QueryType queryXml = QueryDocument.Factory.parse(metaFile).getQuery();

                _log.info(sql);
                _log.info(queryXml.getDescription());
                _log.info(queryXml.getSchemaName());
                _log.info(queryXml.getMetadata());
            }

            // TODO: Check for map.size == 0
        }
    }
}
