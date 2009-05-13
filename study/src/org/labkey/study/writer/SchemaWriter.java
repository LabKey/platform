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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Type;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.xml.StudyDocument.Study.Datasets;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 2:39:33 PM
 */
public class SchemaWriter implements Writer<DataSetDefinition[]>
{
    private static final String SCHEMA_FILENAME = "schema.tsv";

    public void write(DataSetDefinition[] definitions, ExportContext ctx, VirtualFile fs) throws IOException
    {
        Datasets datasetsXml = ctx.getStudyXml().getDatasets();
        Datasets.Schema schemaXml = datasetsXml.addNewSchema();
        String schemaFilename = fs.makeLegalName(SCHEMA_FILENAME);
        schemaXml.setSource(schemaFilename);
        schemaXml.setTypeNameColumn("platename");
        schemaXml.setLabelColumn("platelabel");
        schemaXml.setTypeIdColumn("plateno");

        PrintWriter writer = fs.getPrintWriter(schemaFilename);

        writer.println("platename\tplatelabel\tplateno\tproperty\tlabel\trangeuri\trequired\tformat\tconcepturi\tkey\tautokey");

        for (DataSetDefinition def : definitions)
        {
            String prefix = def.getName() + '\t' + def.getLabel() + '\t' + def.getDataSetId() + '\t';

            TableInfo tinfo = def.getTableInfo(ctx.getUser());

            for (ColumnInfo col : tinfo.getColumns())
            {
                if (col.getName().equals("autokey") || DataSetWriter.shouldExport(col))
                {
                    writer.print(prefix);
                    writer.print(col.getColumnName() + '\t');
                    writer.print(col.getCaption() + '\t');

                    Class clazz = col.getJavaClass();
                    Type t = Type.getTypeByClass(clazz);

                    assert null != t : col.getName() + " has unknown java class " + clazz.getName();

                    writer.print(t.getXsdType() + '\t');
                    writer.print('\t');
                    writer.print(col.isNullable() ? "optional\t" : "required\t");
                    writer.print(StringUtils.trimToEmpty(col.getFormatString()) + "\t");

                    if (col.getName().equals("autokey"))
                        writer.print("1\ttrue");

                    writer.println();
                }
            }
        }

        writer.close();
    }
}
