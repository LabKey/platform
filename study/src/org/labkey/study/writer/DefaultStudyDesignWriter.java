/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.PHI;
import org.labkey.api.data.Results;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.query.StudyQuerySchema;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/24/14.
 */
public abstract class DefaultStudyDesignWriter
{
    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, Set<String> tableNames, StudyQuerySchema schema,
                                  StudyQuerySchema projectSchema, @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        for (String tableName : tableNames)
        {
            StudyQuerySchema.TablePackage tableAndContainer = schema.getTablePackage(ctx, projectSchema, tableName);
            writeTableData(ctx, vf, tableAndContainer.getTableInfo(), getDefaultColumns(ctx, tableAndContainer.getTableInfo()), containerFilter);
        }
    }

    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, TableInfo table, List<ColumnInfo> columns,
                                @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        // Write each table as a separate .tsv
        if (table != null)
        {
            if (containerFilter != null)
            {
                if (table instanceof ContainerFilterable)
                {
                    ((ContainerFilterable)table).setContainerFilter(containerFilter);
                }
            }
//            createExtraForeignKeyColumns(table, columns);                             // TODO: QueryService gets unhappy and seems unnecessary
            Results rs = QueryService.get().select(table, columns, null, null, null, false);
            writeResultsToTSV(rs, vf, getFileName(table));
        }
    }

    protected String getFileName(TableInfo tableInfo)
    {
        return tableInfo.getName().toLowerCase() + ".tsv";
    }

    protected void writeResultsToTSV(Results rs, VirtualFile vf, String fileName) throws SQLException, IOException
    {
        TSVGridWriter tsvWriter = new TSVGridWriter(rs);
        tsvWriter.setApplyFormats(false);
        tsvWriter.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey); // CONSIDER: Use FieldKey instead
        PrintWriter out = vf.getPrintWriter(fileName);
        tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
    }

    /**
     * Returns the default visible columns for a table but ignores the standard columns
     */
    protected List<ColumnInfo> getDefaultColumns(StudyExportContext ctx, TableInfo tableInfo)
    {
        List<ColumnInfo> columns = new ArrayList<>();

        for (ColumnInfo col : tableInfo.getColumns())
        {
            if (FieldKey.fromParts("Container").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("Created").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("CreatedBy").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("Modified").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("ModifiedBy").equals(col.getFieldKey()))
                continue;
            if (!(col.getPHI().isExportLevelAllowed(ctx.getPhiLevel())) && !col.isKeyField())
                continue;

            columns.add(col);
        }
        return columns;
    }

    protected void writeTableInfos(StudyExportContext ctx, VirtualFile vf, Set<String> tableNames, StudyQuerySchema schema, StudyQuerySchema projectSchema, String schemaFileName) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesType tablesXml = tablesDoc.addNewTables();

        for (String tableName : tableNames)
        {
            StudyQuerySchema.TablePackage tablePackage = schema.getTablePackage(ctx, projectSchema, tableName);
            TableInfo tinfo = tablePackage.getTableInfo();
            Domain domain = tinfo.getDomain();
            if (domain != null)
            {
                TableType tableXml = tablesXml.addNewTable();

                List<ColumnInfo> columns = new ArrayList<>();
                Map<String, DomainProperty> propertyMap = new CaseInsensitiveHashMap<>();

                for (DomainProperty prop : domain.getProperties())
                    propertyMap.put(prop.getName(), prop);

                for (ColumnInfo col : tinfo.getColumns())
                {
                    if (!col.isKeyField() && propertyMap.containsKey(col.getName()))
                    {
                        // NOTE: currently these study tables should never be allowed to have columns set at any level of PHI, so this check does nothing at the moment
                        if (!(shouldRemovePhi(ctx.getPhiLevel(), col)))
                            columns.add(col);
                    }
                }
                TableInfoWriter writer = new PropertyTableWriter(tablePackage.getContainer(), tinfo, domain, columns);        // TODO: container correct?
                writer.writeTable(tableXml);
            }
        }
        vf.saveXmlBean(schemaFileName, tablesDoc);
    }

    private static boolean shouldRemovePhi(PHI exportPhiLevel, ColumnInfo column)
    {
        return !(column.getPHI().isExportLevelAllowed(exportPhiLevel));
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testShouldRemovePhi()
        {
            ColumnInfo ciNotPhi = new ColumnInfo("test");
            ciNotPhi.setPHI(PHI.NotPHI);
            ColumnInfo ciLimitedPhi = new ColumnInfo("test");
            ciLimitedPhi.setPHI(PHI.Limited);
            ColumnInfo ciPhi = new ColumnInfo("test");
            ciPhi.setPHI(PHI.PHI);
            ColumnInfo ciRestrictedPhi = new ColumnInfo("test");
            ciRestrictedPhi.setPHI(PHI.Restricted);

            // should remove if it is above PHI export level
            assertTrue(shouldRemovePhi(PHI.PHI, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, ciPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, ciLimitedPhi));

            // shouldn't remove if it is at or below PHI export level
            assertFalse(shouldRemovePhi(PHI.PHI, ciPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.NotPHI, ciNotPhi));
        }
    }

    private static class PropertyTableWriter extends TableInfoWriter
    {
        private final Map<String, DomainProperty> _properties = new CaseInsensitiveHashMap<>();
        private final Domain _domain;

        protected PropertyTableWriter(Container c, TableInfo ti, Domain domain, Collection<ColumnInfo> columns)
        {
            super(c, ti, columns);
            _domain = domain;

            for (DomainProperty prop : _domain.getProperties())
                _properties.put(prop.getName(), prop);
        }

        @Override  // No reason to ever export PropertyURIs
        protected String getPropertyURI(ColumnInfo column)
        {
            return null;
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            // TODO: verify whether this is necessary
            if (_properties.containsKey(column.getName()))
            {
                DomainProperty dp = _properties.get(column.getName());
                if (dp.getName() != null)
                    columnXml.setColumnName(dp.getName());
            }
        }
    }

    public static void createExtraForeignKeyColumns(TableInfo table, Collection<ColumnInfo> columns)
    {
        // Add extra column for lookup from study table to project table, based on numeric key
        if (!StudyQuerySchema.isDataspaceProjectTable(table.getName()))
        {
            List<FieldKey> fieldKeys = new ArrayList<>();
            for (ColumnInfo column : columns)
            {
                ForeignKey fk = column.getFk();
                if (isColumnNumericForeignKeyToDataspaceTable(fk, false))
                {
                    // Add extra column to tsv for numeric foreign key
                    fieldKeys.add(getExtraForeignKeyColumnFieldKey(column, fk));
                }
            }
            Map<FieldKey, ColumnInfo> newColumnMap = QueryService.get().getColumns(table, fieldKeys);
            columns.addAll(newColumnMap.values());
        }
    }

    public static boolean isColumnNumericForeignKeyToDataspaceTable(ForeignKey fk, boolean includeFolderLevel)
    {
        if (null != fk)
        {
            String lookupColumnName = fk.getLookupColumnName();
            TableInfo lookupTableInfo = fk.getLookupTableInfo();
            if (null != lookupTableInfo && null != lookupColumnName &&
                lookupTableInfo.getColumn(lookupColumnName).getJdbcType().isNumeric() &&
                (StudyQuerySchema.isDataspaceProjectTable(lookupTableInfo.getName()) ||
                    (includeFolderLevel && StudyQuerySchema.isDataspaceFolderTable(lookupTableInfo.getName()))))
            {
                return true;
            }
        }
        return false;
    }

    public static FieldKey getExtraForeignKeyColumnFieldKey(ColumnInfo column, ForeignKey fk)
    {
        TableInfo lookupTableInfo = fk.getLookupTableInfo();
        String displayName = fk.getLookupDisplayName();
        if (null == displayName)
            displayName = lookupTableInfo.getTitleColumn();
        return FieldKey.fromParts(column.getName(), displayName);   // TODO: push into ForeignKey
    }
}
