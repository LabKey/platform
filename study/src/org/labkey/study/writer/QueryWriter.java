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

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.VirtualFile;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;
import org.labkey.study.model.Study;
import org.apache.xmlbeans.XmlObject;

import java.io.PrintWriter;
import java.util.List;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:49:55 PM
 */
public class QueryWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        Container c = study.getContainer();
        List<QueryDefinition> queries = QueryService.get().getQueryDefs(c);

        for (QueryDefinition query : queries)
        {
            String path = "queries/" + fs.makeLegalName(query.getSchemaName());
            fs.makeDir(path);

            String baseName = path + "/" + fs.makeLegalName(query.getName());
            PrintWriter sql = fs.getPrintWriter(baseName + ".sql");   // TODO: ModuleQueryDef.FILE_EXTENSION
            sql.println(query.getSql());
            sql.close();

            // TODO: Set other properties
            QueryType qtDoc = QueryType.Factory.newInstance();
            qtDoc.setDescription(query.getDescription());

            if (false)
            {
                XmlObject metadata = XmlObject.Factory.newValue(query.getMetadataXml());  // TODO: Does not work at all
                qtDoc.setMetadata(metadata);
            }

            QueryDocument qDoc = QueryDocument.Factory.newInstance();
            qDoc.setQuery(qtDoc);

            PrintWriter xml = fs.getPrintWriter(baseName + ".query.xml");    // TODO: ModuleQueryDef.META_FILE_EXTENSION
            qDoc.save(xml);               // TODO: Set options for namespace, indenting
            xml.close();
        }
    }
}
