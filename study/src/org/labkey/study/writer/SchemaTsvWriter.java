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
import org.labkey.api.exp.property.Type;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.xml.StudyDocument.Study.Datasets;
import org.labkey.study.importer.StudyImporter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 2:39:33 PM
 */
public class SchemaTsvWriter implements Writer<List<DataSetDefinition>, StudyExportContext>
{
    public static final String FILENAME = "schema.tsv";

    public String getSelectionText()
    {
        return "Dataset Schema Description";
    }

    public void write(List<DataSetDefinition> definitions, StudyExportContext ctx, VirtualFile fs) throws IOException, StudyImporter.StudyImportException
    {
        Datasets datasetsXml = ctx.getStudyXml().getDatasets();
        Datasets.Schema schemaXml = datasetsXml.addNewSchema();
        String schemaFilename = fs.makeLegalName(FILENAME);
        schemaXml.setFile(schemaFilename);
        schemaXml.setTypeNameColumn("platename");
        schemaXml.setLabelColumn("platelabel");
        schemaXml.setTypeIdColumn("plateno");

        PrintWriter writer = fs.getPrintWriter(schemaFilename);

        writer.println("platename\tplatelabel\tplateno\tproperty\tlabel\trangeuri\trequired\tformat\tconcepturi\tkey\tautokey");

        for (DataSetDefinition def : definitions)
        {
            String prefix = def.getName() + '\t' + def.getLabel() + '\t' + def.getDataSetId() + '\t';

            TableInfo tinfo = def.getTableInfo(ctx.getUser());
            String visitDatePropertyName = def.getVisitDatePropertyName();

            for (ColumnInfo col : DatasetWriter.getColumnsToExport(tinfo, def, true))
            {
                writer.print(prefix);
                writer.print(col.getColumnName() + '\t');
                writer.print(col.getLabel() + '\t');

                Class clazz = col.getJavaClass();
                Type t = Type.getTypeByClass(clazz);

                if (null == t)
                    throw new IllegalStateException(col.getName() + " in dataset " + def.getName() + " (" + def.getLabel() + ") has unknown java class " + clazz.getName());

                writer.print(t.getXsdType());
                writer.print('\t');
                writer.print(col.isNullable() ? "optional\t" : "required\t");
                writer.print(StringUtils.trimToEmpty(col.getFormatString()) + "\t");     // TODO: Only export if non-null / != default

                // TODO: Export all ConceptURIs, not just visit date tag?
                if (col.getColumnName().equals(visitDatePropertyName))
                    writer.print(DataSetDefinition.getVisitDateURI());

                writer.print("\t");

                if (col.getName().equals(def.getKeyPropertyName()))
                {
                    writer.print("1");

                    if (def.isKeyPropertyManaged())
                        writer.print("\ttrue");
                }

                // TODO: mvEnabled?  category?  hidden?  lookup properties (folder path, schema, query)?

                writer.println();
            }
        }

        writer.close();
    }
}
