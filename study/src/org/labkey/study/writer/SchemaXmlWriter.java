/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.IndexInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.assay.SpecimenForeignKey;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.IndexType;
import org.labkey.data.xml.IndicesType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.StudyDocument;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 27, 2009
 * Time: 11:12:33 AM
 */
public class SchemaXmlWriter implements Writer<List<DatasetDefinition>, ImportContext<StudyDocument.Study>>
{
    public static final String SCHEMA_FILENAME = "datasets_metadata.xml";

    private final Set<String> _candidatePropertyURIs = new HashSet<>();   // Allows nulls

    public SchemaXmlWriter()
    {
        // We export only the standard study propertyURIs and the SystemProperty propertyURIs (special EHR properties,
        // etc.); see #12742.  We could have a registration mechanism for this... but this seems good enough for now.
        for (PropertyDescriptor pd : DatasetDefinition.getStandardPropertiesMap().values())
            _candidatePropertyURIs.add(pd.getPropertyURI());

        for (PropertyDescriptor pd: SystemProperty.getProperties())
            _candidatePropertyURIs.add(pd.getPropertyURI());
    }

    public String getDataType()
    {
        return StudyArchiveDataTypes.DATASET_SCHEMA_DEFINITION;
    }

    public void write(List<DatasetDefinition> definitions, ImportContext<StudyDocument.Study> ctx, VirtualFile vf) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesType tablesXml = tablesDoc.addNewTables();

        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        for (DatasetDefinition def : definitions)
        {
            TableType tableXml = tablesXml.addNewTable();
            if (def.getType().equals(Dataset.TYPE_PLACEHOLDER))
            {
                PlaceholderDatasetWriter w = new PlaceholderDatasetWriter(def);
                w.writeTable(tableXml);
            }
            else
            {
                TableInfo ti = schema.getTable(def.getName());
                DatasetTableInfoWriter w = new DatasetTableInfoWriter(ti, def, ctx.getPhiLevel());
                w.writeTable(tableXml);
            }
        }

        vf.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
    }

    private class PlaceholderDatasetWriter
    {
        private final DatasetDefinition _def;

        private PlaceholderDatasetWriter(DatasetDefinition def)
        {
            _def = def;
        }

        public void writeTable(TableType tableXml)
        {
            tableXml.setTableName(_def.getName());
            tableXml.setTableDbType("TABLE");
            if (null != _def.getLabel())
                tableXml.setTableTitle(_def.getLabel());
            if (null != _def.getDescription())
                tableXml.setDescription(_def.getDescription());
        }
    }

    private class DatasetTableInfoWriter extends TableInfoWriter
    {
        private final DatasetDefinition _def;
        private final Collection<IndexInfo> _indices;

        private DatasetTableInfoWriter(TableInfo ti, DatasetDefinition def, PHI exportPhiLevel)
        {
            super(def.getContainer(), ti, DatasetDataWriter.getColumnsToExport(ti, def, true, exportPhiLevel));
            _def = def;
            _indices = DatasetDataWriter.getIndicesToExport(ti);
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);

            tableXml.setTableName(_def.getName());  // Use dataset name, not temp table name
            if (null != _def.getLabel())
                tableXml.setTableTitle(_def.getLabel());
            if (null != _def.getDescription())
                tableXml.setDescription(_def.getDescription());

            writeTableIndices(tableXml);
        }

        private void writeTableIndices(TableType tableXml)
        {
            if(_indices.size() > 0)
            {
                IndicesType indicesXml = tableXml.addNewIndices();
                for (IndexInfo indexInfo : _indices)
                {
                    IndexType indexType = indicesXml.addNewIndex();
                    writeIndex(indexInfo, indexType);
                }
            }
        }

        private void writeIndex(IndexInfo indexInfo, IndexType indexXml)
        {
            indexXml.setType(indexInfo.getType().getXmlIndexType());

            for (String columnName :  indexInfo.getColumns())
            {
                indexXml.addColumn(columnName);
            }
        }


        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            if (column.getFk() instanceof SpecimenForeignKey)
            {
                // SpecimenForeignKey is a special FK implementation that won't be wired up correctly at import time, so
                // exclude it from the export
                columnXml.unsetFk();
            }
            else if (column.getFk() != null)
            {
                if (column.getFk().getLookupTableInfo() instanceof ExpRunTable)
                {
                    // We're not exporting assay runs, so it's useless to have a lookup that's looking for them
                    // after the dataset has been imported
                    columnXml.unsetFk();
                }
            }

            if (column.getName() != null && column.getName().contains(".") && columnXml.isSetNullable() && !columnXml.getNullable())
            {
                // Assay datasets contain columns that are flattened from lookups. The lookup target may not allow
                // nulls in its own table, but if the parent column is nullable, the joined result might be null.
                columnXml.unsetNullable();
            }
            
            if (column.getURL() != null && column.getURL().getSource().startsWith("/assay/assayDetailRedirect.view?"))
            {
                // We don't include assay runs in study exports, so the link target won't be available in the target system
                columnXml.unsetUrl();
            }
            // TODO: The fix for Issue 20302 in ListWriter writes the property descriptor's URL, which has the substitution markers (eg :none)
            // Similar fix here would be nice, but property descriptors are not readily available.

            if (column.isUnselectable())
            {
                // Still export the underlying value, but since we don't support unselectable as an attribute in
                // export/import, do the next best thing and just hide the column
                columnXml.setIsHidden(true);
            }

            String columnName = column.getName();
            if (columnName.equals(_def.getKeyPropertyName()))
            {
                columnXml.setIsKeyField(true);

                if (_def.getKeyManagementType() == Dataset.KeyManagementType.RowId)
                    columnXml.setIsAutoInc(true);
                else if (_def.getKeyManagementType() == Dataset.KeyManagementType.GUID)
                    columnXml.setDatatype("entityid");
            }
        }

        @Override
        protected String getPropertyURI(ColumnInfo column)
        {
            String propertyURI = column.getPropertyURI();

            // Only round-trip the special PropertyURIs.  See #12742.
            if (_candidatePropertyURIs.contains(propertyURI))
                return propertyURI;
            else
                return null;
        }

        @Override
        protected String getConceptURI(ColumnInfo column)
        {
            String conceptURI = super.getConceptURI(column);

            if (null != conceptURI)
                return conceptURI;

            // Proper ConceptURI support is not implemented, but we use the 'VisitDate' concept in this isolated spot
            // as a marker to indicate which dataset column should be tagged as the visit date column during import:
            if (column.getName().equalsIgnoreCase(_def.getVisitDateColumnName()))
                return DatasetDefinition.getVisitDateURI();
            return null;
        }
    }
}