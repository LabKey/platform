package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.xml.StudyDocument.Study.Datasets;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 2:39:33 PM
 */
public class SchemaWriter implements Writer<DataSetDefinition[]>
{
    private static final String SCHEMA_FILENAME = "schema.tsv";

    public void write(DataSetDefinition[] definitions, ExportContext ctx, VirtualFile fs) throws FileNotFoundException, UnsupportedEncodingException
    {
        Datasets datasetsXml = ctx.getStudyXml().getDatasets();
        Datasets.Schema schemaXml = datasetsXml.addNewSchema();
        String schemaFilename = fs.makeLegalName(SCHEMA_FILENAME);
        schemaXml.setSource(schemaFilename);
        schemaXml.setTypeNameColumn("platename");
        schemaXml.setLabelColumn("platelabel");
        schemaXml.setTypeIdColumn("plateno");

        PrintWriter writer = fs.getPrintWriter(schemaFilename);

        writer.println("platename\tplatelabel\tplateno\tproperty\tlabel\trangerui\trequired\tformat\tconcepturi\tkey\tautokey");

        for (DataSetDefinition def : definitions)
        {
            String prefix = def.getName() + '\t' + def.getLabel() + '\t' + def.getDataSetId() + '\t';

            TableInfo tinfo = def.getTableInfo(ctx.getUser());

            for (ColumnInfo col : tinfo.getColumns())
            {
                if (DataSetWriter.shouldExport(col))
                {
                    writer.print(prefix);
                    writer.print(col.getColumnName() + '\t');
                    writer.print(col.getCaption() + '\t');
                    writer.print('\t');
                    writer.print(col.isNullable() ? "optional\t" : "required\t");
                    writer.print(StringUtils.trimToEmpty(col.getFormatString()) + "\t");

                    writer.println();
                }
            }
        }

        writer.close();
    }
}
