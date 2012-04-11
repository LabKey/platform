package org.labkey.query;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.externalSchemaDef.ExternalSchemaDefDocument;
import org.labkey.data.xml.externalSchemaDef.ExternalSchemaDefType;
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
    private static final String DEFAULT_DIRECTORY = "externalSchemaDefs";
    public static final String FILE_EXTENSION =  ".extschemadef.xml";

    @Override
    public FolderWriter create()
    {
        return new ExternalSchemaDefWriter();
    }

    public class ExternalSchemaDefWriter implements FolderWriter
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
                ctx.getXml().addNewExternalSchemaDefs().setDir(DEFAULT_DIRECTORY);
                VirtualFile extSchemaDefsDir = vf.getDir(DEFAULT_DIRECTORY);

                for (ExternalSchemaDef def : defs)
                {
                    ExternalSchemaDefDocument defDoc = ExternalSchemaDefDocument.Factory.newInstance();
                    ExternalSchemaDefType defXml = defDoc.addNewExternalSchemaDef();
                    defXml.setDataSource(def.getDataSource());
                    defXml.setDbSchemaName(def.getDbSchemaName());
                    defXml.setUserSchemaName(def.getUserSchemaName());
                    defXml.setEditable(def.isEditable());
                    defXml.setIndexable(def.isIndexable());

                    ExternalSchemaDefType.Tables tablesXml = defXml.addNewTables();
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
                        XmlObject xObj = XmlObject.Factory.parse(def.getMetaData());
                        defXml.setMetadata(xObj);
                    }

                    extSchemaDefsDir.saveXmlBean(def.getUserSchemaName() + FILE_EXTENSION, defDoc);
                }
            }
        }
    }
}
