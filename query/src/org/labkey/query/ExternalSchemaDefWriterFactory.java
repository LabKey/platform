/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.MultiTablesType;
import org.labkey.data.xml.externalSchema.ExternalSchemaDocument;
import org.labkey.data.xml.externalSchema.ExternalSchemaType;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.QueryManager;

import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class ExternalSchemaDefWriterFactory implements FolderWriterFactory
{
    private static final String DEFAULT_DIRECTORY = "externalSchemas";
    public static final String FILE_EXTENSION =  ".externalschema.xml";   

    @Override
    public FolderWriter create()
    {
        return new ExternalSchemaDefWriter();
    }

    public class ExternalSchemaDefWriter extends BaseFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "External schema definitions";
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            List<ExternalSchemaDef> defs = Arrays.asList(QueryManager.get().getExternalSchemaDefs(c));
            if (defs.size() > 0)
            {
                ctx.getXml().addNewExternalSchemas().setDir(DEFAULT_DIRECTORY);
                VirtualFile extSchemasDir = vf.getDir(DEFAULT_DIRECTORY);

                for (ExternalSchemaDef def : defs)
                {
                    ExternalSchemaDocument defDoc = ExternalSchemaDocument.Factory.newInstance();
                    ExternalSchemaType defXml = defDoc.addNewExternalSchema();
                    defXml.setDataSource(def.getDataSource());
                    defXml.setDbSchemaName(def.getDbSchemaName());
                    defXml.setUserSchemaName(def.getUserSchemaName());
                    defXml.setEditable(def.isEditable());
                    defXml.setIndexable(def.isIndexable());

                    ExternalSchemaType.Tables tablesXml = defXml.addNewTables();
                    String tables = def.getTables();
                    if (tables.equals("*"))
                    {
                        tablesXml.addTableName("*");
                    }
                    else
                    {
                        for (String table : tables.split(","))
                        {
                            tablesXml.addTableName(table);
                        }
                    }

                    if (null != def.getMetaData())
                    {
                        MultiTablesType metaDataXml = MultiTablesType.Factory.parse(def.getMetaData());
                        defXml.setMetadata(metaDataXml);
                    }

                    extSchemasDir.saveXmlBean(def.getUserSchemaName() + FILE_EXTENSION, defDoc);
                }
            }
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
