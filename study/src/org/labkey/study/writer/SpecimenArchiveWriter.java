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

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.writer.StandardSpecimenWriter.QueryInfo;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:28:37 AM
 */
public class SpecimenArchiveWriter extends AbstractSpecimenWriter
{
    public static final String SCHEMA_FILENAME = "specimens_metadata.xml";

    public String getDataType()
    {
        return StudyArchiveDataTypes.SPECIMENS;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.Specimens specimensXml = ensureSpecimensElement(ctx);
        VirtualFile specimensDir = root.getDir(DEFAULT_DIRECTORY);
        String archiveName = specimensDir.makeLegalName(study.getShortName() + ".specimens");

        try (VirtualFile zip = specimensDir.createZipArchive(archiveName))
        {
            if (!zip.equals(specimensDir)) // MemoryVirtualFile doesn't add a zip archive, it just returns vf
                specimensXml.setFile(archiveName);

            StudySchema schema = StudySchema.getInstance();

            new LocationSpecimenWriter().write(new QueryInfo(schema.getTableInfoSite(ctx.getContainer()), "labs", SpecimenImporter.SITE_COLUMNS), ctx, zip);
            new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoSpecimenPrimaryType(ctx.getContainer()), "primary_types", SpecimenImporter.PRIMARYTYPE_COLUMNS), ctx, zip);
            new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoSpecimenAdditive(ctx.getContainer()), "additives", SpecimenImporter.ADDITIVE_COLUMNS), ctx, zip);
            new StandardSpecimenWriter().write(new QueryInfo(schema.getTableInfoSpecimenDerivative(ctx.getContainer()), "derivatives", SpecimenImporter.DERIVATIVE_COLUMNS), ctx, zip);

            new SpecimenWriter().write(study, ctx, zip);
        }

        // Create specimens metadata file and write out the table infos for the specimens, event, and vial tables
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        SpecimenTablesProvider tablesProvider = new SpecimenTablesProvider(ctx.getContainer(), ctx.getUser(), null);
        TablesType tablesXml = tablesDoc.addNewTables();
        Map<String, TableInfo> specimenTables = new HashMap<>();

        TableInfo eventTable = tablesProvider.getTableInfoIfExists(SpecimenTablesProvider.SPECIMENEVENT_TABLENAME);
        if (eventTable != null)
            specimenTables.put(SpecimenTablesProvider.SPECIMENEVENT_TABLENAME, eventTable);

        TableInfo specimenTable = tablesProvider.getTableInfoIfExists(SpecimenTablesProvider.SPECIMEN_TABLENAME);
        if (specimenTable != null)
            specimenTables.put(SpecimenTablesProvider.SPECIMEN_TABLENAME, specimenTable);

        TableInfo vialTable = tablesProvider.getTableInfoIfExists(SpecimenTablesProvider.VIAL_TABLENAME);
        if (vialTable != null)
            specimenTables.put(SpecimenTablesProvider.VIAL_TABLENAME, vialTable);

        for (Map.Entry<String, TableInfo> entry : specimenTables.entrySet())
        {
            TableType tableXml = tablesXml.addNewTable();

            Domain domain = tablesProvider.getDomain(entry.getKey(), false);
            TableInfo table = entry.getValue();
            List<ColumnInfo> columns = new ArrayList<>();

            for (ColumnInfo col : table.getColumns())
            {
                if (!shouldRemovePhi(ctx.getPhiLevel(), col) || !col.isKeyField())
                    columns.add(col);
            }

            SpecimenTableInfoWriter xmlWriter = new SpecimenTableInfoWriter(ctx.getContainer(), table, entry.getKey(), domain, columns);
            xmlWriter.writeTable(tableXml);
        }
        specimensDir.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
    }

    private static boolean shouldRemovePhi(PHI exportPhiLevel, ColumnInfo column)
    {
        return !column.getPHI().isExportLevelAllowed(exportPhiLevel);
    }

    @Override
    public boolean includeWithTemplate()
    {
        return false;
    }

    private static class SpecimenTableInfoWriter extends TableInfoWriter
    {
        private final Map<String, DomainProperty> _properties = new CaseInsensitiveHashMap<>();
        private final Domain _domain;
        private final String _name;

        protected SpecimenTableInfoWriter(Container c, TableInfo ti, String tableName, Domain domain, Collection<ColumnInfo> columns)
        {
            super(c, ti, columns);
            _domain = domain;
            _name = tableName;

            for (DomainProperty prop : _domain.getProperties())
                _properties.put(prop.getName(), prop);
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);
            tableXml.setTableName(_name);  // Use specimen table name, not provisioned storage name
        }

        @Override  // No reason to ever export list PropertyURIs
        protected String getPropertyURI(ColumnInfo column)
        {
            return null;
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            // Since the specimen tables only return a SchemaTableInfo, the column names will be decapitalized,
            // to preserve casing we defer to the DomainProperty names. This is mostly cosmetic
            if (_properties.containsKey(column.getName()))
            {
                DomainProperty dp = _properties.get(column.getName());
                if (dp.getName() != null)
                    columnXml.setColumnName(dp.getName());
                if (0 != dp.getScale())
                    columnXml.setScale(dp.getScale());
            }
        }
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

            // should remove if it is at or above PHI export level
            assertTrue(shouldRemovePhi(PHI.Restricted, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.PHI, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.PHI, ciPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciLimitedPhi));

            // shouldn't remove if it is not at or above PHI export level
            assertFalse(shouldRemovePhi(PHI.Restricted, ciPhi));
            assertFalse(shouldRemovePhi(PHI.Restricted, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.Restricted, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, ciNotPhi));
        }
    }
}
