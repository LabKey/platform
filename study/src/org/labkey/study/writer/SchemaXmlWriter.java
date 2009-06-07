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

import org.labkey.api.data.*;
import org.labkey.api.exp.property.Type;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.VirtualFile;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.study.model.DataSetDefinition;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * User: adam
 * Date: May 27, 2009
 * Time: 11:12:33 AM
 */
public class SchemaXmlWriter implements Writer<DataSetDefinition[]>
{
    public static final String SCHEMA_FILENAME = "datasets_metadata.xml";

    private String _defaultDateFormat;

    public SchemaXmlWriter(String defaultDateFormat)
    {
        _defaultDateFormat = defaultDateFormat;
    }

    public String getSelectionText()
    {
        return "Dataset Schema Description";
    }

    public void write(DataSetDefinition[] definitions, ExportContext ctx, VirtualFile fs) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesDocument.Tables tablesXml = tablesDoc.addNewTables();

        for (DataSetDefinition def : definitions)
        {
            TableInfo ti = def.getTableInfo(ctx.getUser());
            List<ColumnInfo> allColumns = ti.getColumns();
            List<ColumnInfo> columns = DatasetWriter.getColumnsToExport(allColumns, def, true);

            // Write metadata
            TableType tableXml = tablesXml.addNewTable();
            tableXml.setTableName(def.getName());
            tableXml.setTableDbType("TABLE");
            if (null != def.getLabel())
                tableXml.setTableTitle(def.getLabel());
            if (null != def.getDescription())
                tableXml.setDescription(def.getDescription());
            TableType.Columns columnsXml = tableXml.addNewColumns();

            if (null == _defaultDateFormat)
                _defaultDateFormat = DateUtil.getStandardDateFormatString();

            for (ColumnInfo column : columns)
            {
                ColumnType columnXml = columnsXml.addNewColumn();
                String columnName = column.getName();
                columnXml.setColumnName(columnName);

                Class clazz = column.getJavaClass();
                Type t = Type.getTypeByClass(clazz);

                if (null == t)
                    throw new IllegalStateException(columnName + " in dataset " + def.getName() + " (" + def.getLabel() + ") has unknown java class " + clazz.getName());

                columnXml.setDatatype(t.getSqlTypeName());

                if (null != column.getCaption())
                    columnXml.setColumnTitle(column.getCaption());

                if (null != column.getDescription())
                    columnXml.setDescription(column.getDescription());

                if (!column.isNullable())
                    columnXml.setNullable(false);

                String formatString = column.getFormatString();

                // Write only if it's non-null (and in the case of dates, different from the global default)
                if (null != formatString && (!Date.class.isAssignableFrom(column.getJavaClass()) || !formatString.equals(_defaultDateFormat)))
                    columnXml.setFormatString(formatString);

                if (null != column.getMvColumnName())
                    columnXml.setMvColumnName(column.getMvColumnName());

                ForeignKey fk = column.getFk();

                if (null != fk && null != fk.getLookupColumnName())
                {
                    ColumnType.Fk fkXml = columnXml.addNewFk();
                    Container lkContainer = (null != fk.getLookupContainerId() ? ContainerManager.getForId(fk.getLookupContainerId()) : null);
                    fkXml.setFkFolderPath(lkContainer != null ? lkContainer.getPath() : null);

                    TableInfo tinfo = fk.getLookupTableInfo();
                    fkXml.setFkDbSchema(tinfo.getPublicSchemaName());
                    fkXml.setFkTable(tinfo.getPublicName());
                    fkXml.setFkColumnName(fk.getLookupColumnName());
                }

                if (columnName.equals(def.getKeyPropertyName()))
                {
                    columnXml.setIsKeyField(true);

                    if (def.isKeyPropertyManaged())
                        columnXml.setIsAutoInc(true);
                }

                // TODO: Field validators?
                // TODO: Default values / Default value types
                // TODO: ConceptURI
            }
        }

        XmlBeansUtil.saveDoc(fs.getPrintWriter(SCHEMA_FILENAME), tablesDoc);
    }
}