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
