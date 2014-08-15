/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Type;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.StudyDocument.Study.Datasets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 2:39:33 PM
 */
public class SchemaTsvWriter implements Writer<List<DataSetDefinition>, ImportContext<StudyDocument.Study>>
{
    public static final String FILENAME = "schema.tsv";

    private static final String TYPE_NAME_COLUMN = "platename";
    private static final String LABEL_COLUMN = "platelabel";
    private static final String TYPE_ID_COLUMN = "plateno";

    public String getSelectionText()
    {
        return "Dataset Schema Description";
    }

    public void write(List<DataSetDefinition> definitions, ImportContext<StudyDocument.Study> ctx, VirtualFile vf) throws IOException, ImportException
    {
        Datasets datasetsXml = ctx.getXml().getDatasets();
        Datasets.Schema schemaXml = datasetsXml.addNewSchema();
        String schemaFilename = vf.makeLegalName(FILENAME);
        schemaXml.setFile(schemaFilename);
        schemaXml.setTypeNameColumn(TYPE_NAME_COLUMN);
        schemaXml.setLabelColumn(LABEL_COLUMN);
        schemaXml.setTypeIdColumn(TYPE_ID_COLUMN);

        try (PrintWriter writer = vf.getPrintWriter(schemaFilename))
        {
            writeDatasetSchema(ctx, definitions, writer);
        }
    }

    public void writeDatasetSchema(ImportContext<StudyDocument.Study> ctx, List<DataSetDefinition> definitions, PrintWriter writer)
    {
        writer.println(TYPE_NAME_COLUMN + "\t" + LABEL_COLUMN + "\t" + TYPE_ID_COLUMN + "\thidden\tproperty\tlabel\trangeuri\trequired\tformat\tconcepturi\tmvenabled\tkey\tautokey");

        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        for (DataSetDefinition def : definitions)
        {
            String prefix = def.getName() + '\t' + def.getLabel() + '\t' + def.getDatasetId() + '\t' + (def.isShowByDefault() ? "\t" : "true\t");

            TableInfo tinfo = schema.createDatasetTableInternal(def);
            String visitDatePropertyName = def.getVisitDateColumnName();

            for (ColumnInfo col : DatasetWriter.getColumnsToExport(tinfo, def, true, ctx.isRemoveProtected()))
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
                writer.print(StringUtils.trimToEmpty(col.getFormat()) + "\t");

                // TODO: Export all ConceptURIs, not just visit date tag?
                if (col.getColumnName().equals(visitDatePropertyName))
                    writer.print(DataSetDefinition.getVisitDateURI());

                writer.print("\t");
                writer.print(col.isMvEnabled() ? "true\t" : "\t");

                if (col.getName().equals(def.getKeyPropertyName()))
                {
                    writer.print("1");
                    writer.print("\t" + def.getKeyManagementType().getSerializationName());
                }

                // TODO: Category?

                writer.println();
            }
        }
    }
}
