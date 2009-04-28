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

import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.StudyDocument.Study.Datasets;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.VirtualFile;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:10:37 PM
 */
public class DataSetWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws SQLException, FileNotFoundException, UnsupportedEncodingException, ServletException
    {
        StudyDocument.Study studyXml = ctx.getStudyXml();
        Datasets datasetsXml = studyXml.addNewDatasets();

        DataSetDefinition[] datasets = study.getDataSets();

        // Write out the schema.tsv file and add reference & attributes to study.xml
        SchemaWriter schemaWriter = new SchemaWriter();
        schemaWriter.write(datasets, ctx, fs);

        // Write out the .dataset file and add reference to study.xml
        Datasets.Definition definitionXml = datasetsXml.addNewDefinition();
        String datasetFilename = fs.makeLegalName(study.getLabel().replaceAll("\\s", "") + ".dataset");
        definitionXml.setSource(datasetFilename);

        PrintWriter writer = fs.getPrintWriter(datasetFilename);
        writer.println("# default group can be used to avoid repeating definitions for each dataset\n" +
                "#\n" +
                "# action=[REPLACE,APPEND,DELETE] (default:REPLACE)\n" +
                "# deleteAfterImport=[TRUE|FALSE] (default:FALSE)\n" +
                "\n" +
                "default.action=REPLACE\n" +
                "default.deleteAfterImport=FALSE\n" +
                "\n" +
                "# map a source tsv column (right side) to a property name or full propertyURI (left)\n" +
                "# predefined properties: ParticipantId, SiteId, VisitId, Created\n" +
                "default.property.ParticipantId=ptid\n" +
                "default.property.Created=dfcreate\n" +
                "\n" +
                "# use to map from filename->datasetid\n" +
                "# NOTE: if there are NO explicit import definitions, we will try to import all files matching pattern\n" +
                "# NOTE: if there are ANY explicit mapping, we will only import listed datasets\n" +
                "\n" +
                "default.filePattern=plate(\\\\d\\\\d\\\\d).tsv\n" +
                "default.importAllMatches=TRUE");
        writer.close();

        for (DataSetDefinition def : datasets)
        {
            TableInfo ti = def.getTableInfo(ctx.getUser());
            List<ColumnInfo> allColumns = ti.getColumns();
            List<ColumnInfo> columns = new ArrayList<ColumnInfo>(allColumns.size());

            for (ColumnInfo col : allColumns)
                if (shouldExport(col))
                    columns.add(col);

            ResultSet rs = Table.select(ti, columns, null, null);
            TSVGridWriter tsvWriter = new TSVGridWriter(rs);
            tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
            PrintWriter out = fs.getPrintWriter(def.getFileName());
            tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
        }
    }

    public static boolean shouldExport(ColumnInfo column)
    {
        if (!column.isUserEditable())
            return false;

        String propertyURI = column.getPropertyURI();

        // TODO: Beter check for this?
        return !(propertyURI.equals(DataSetDefinition.getParticipantIdURI()) || propertyURI.equals(DataSetDefinition.getSequenceNumURI()));
    }
}
