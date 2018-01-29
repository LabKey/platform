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

package org.labkey.list.model;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileNameUniquifier;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.list.view.ListItemAttachmentParent;
import org.labkey.list.xml.ListsDocument;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class ListWriter
{
    static final String SCHEMA_FILENAME = "lists.xml";
    static final String SETTINGS_FILENAME = "settings.xml";

    public boolean write(Container c, User user, VirtualFile listsDir, ImportContext ctx) throws Exception
    {
        Map<String, ListDefinition> lists = ListService.get().getLists(c);
        PHI exportPhiLevel = (ctx != null) ? ctx.getPhiLevel() : PHI.NotPHI;

        if (!lists.isEmpty())
        {
            if (ctx != null && ctx.getClass().equals(FolderExportContext.class))
            {
                Set<Integer> listsToExport = ((FolderExportContext)ctx).getListIds();
                if (listsToExport != null)
                {
                    // If we have a list of lists to export then we only want to export the ones in the list.
                    // If the list is null then we want to grab all of them, so we don't do anything the lists map.
                    Iterator<String> listIt = lists.keySet().iterator();
                    while (listIt.hasNext())
                    {
                        String key = listIt.next();
                        ListDefinition list = lists.get(key);
                        if (!listsToExport.contains(list.getListId()))
                        {
                            listIt.remove();
                        }
                    }
                }
            }
            // Create meta data doc
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();

            // Create doc for non-table settings (discussions, indexing settings & templates)
            ListsDocument listSettingsDoc = ListsDocument.Factory.newInstance();
            ListsDocument.Lists listSettingsXml = listSettingsDoc.addNewLists();

            // Insert standard comment explaining where the data lives, who exported it, and when
            XmlBeansUtil.addStandardExportComment(tablesXml, c, user);

            ListQuerySchema schema = new ListQuerySchema(user, c);

            for (Map.Entry<String, ListDefinition> entry : lists.entrySet())
            {
                ListDefinition def = entry.getValue();
                TableInfo ti = schema.getTable(def.getName());

                // Continue exporting other lists if a TableInfo can't be loaded. One possible scenario: extra long column names
                // with MV indicators cause errors right now. We need to fix that, but export also needs to be resilient.
                if (null == ti)
                    continue;

                // Write meta data
                TableType tableXml = tablesXml.addNewTable();
                ListTableInfoWriter xmlWriter = new ListTableInfoWriter(ti, def, getColumnsToExport(ti, true, exportPhiLevel));
                xmlWriter.writeTable(tableXml);

                // Write settings
                ListsDocument.Lists.List settings = listSettingsXml.addNewList();
                writeSettings(settings, def);

                // Write data
                Collection<ColumnInfo> columns = getColumnsToExport(ti, false, exportPhiLevel);

                if (null != ctx && ctx.isAlternateIds())
                {
                    createAlternateIdColumns(ti, columns, ctx.getContainer());
                }

                if (!columns.isEmpty())
                {
                    List<DisplayColumn> displayColumns = columns
                        .stream()
                        .filter(Objects::nonNull)
                        .map(ListExportDataColumn::new)
                        .collect(Collectors.toCollection(LinkedList::new));

                    // Sort the data rows by PK, #11261
                    Sort sort = ti.getPkColumnNames().size() != 1 ? null : new Sort(ti.getPkColumnNames().get(0));

                    Results rs = QueryService.get().select(ti, columns, null, sort);
                    TSVGridWriter tsvWriter = new TSVGridWriter(rs, displayColumns);
                    tsvWriter.setApplyFormats(false);
                    tsvWriter.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey); // CONSIDER: Use FieldKey instead
                    PrintWriter out = listsDir.getPrintWriter(def.getName() + ".tsv");
                    tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet

                    writeAttachments(ti, def, c, listsDir, exportPhiLevel);
                }
            }

            listsDir.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
            listsDir.saveXmlBean(SETTINGS_FILENAME, listSettingsDoc);

            return true;
        }
        else
        {
            return false;
        }
    }

    public static void createAlternateIdColumns(TableInfo ti, Collection<ColumnInfo> columns, Container c)
    {

        Collection<ColumnInfo> colCopy = new LinkedList<>(columns);
        String participantIdColumnName = StudyService.get().getSubjectColumnName(c);
        for (ColumnInfo column : colCopy)
        {
            if((column.getConceptURI() != null && column.getConceptURI().equals("http://cpas.labkey.com/Study#ParticipantId"))
                    || column.getName().equalsIgnoreCase(participantIdColumnName))
            {
                ColumnInfo newColumn = StudyService.get().createAlternateIdColumn(ti, column, c);
                columns.remove(column);
                columns.add(newColumn);
            }
        }
    }

    private void writeSettings(ListsDocument.Lists.List settings, ListDefinition def)
    {
        settings.setName(def.getName());
        settings.setId(def.getListId());

        if (def.getDiscussionSetting().getValue() != 0) settings.setDiscussions(def.getDiscussionSetting().getValue());
        if (!def.getAllowDelete()) settings.setAllowDelete(def.getAllowDelete());
        if (!def.getAllowUpload()) settings.setAllowUpload(def.getAllowUpload());
        if (!def.getAllowExport()) settings.setAllowExport(def.getAllowExport());

        if (def.getEachItemIndex()) settings.setEachItemIndex(def.getEachItemIndex());
        if (def.getEachItemTitleSetting().getValue() != 0) settings.setEachItemTitleSetting(def.getEachItemTitleSetting().getValue());
        if (null != def.getEachItemTitleTemplate()) settings.setEachItemTitleTemplate(def.getEachItemTitleTemplate());
        if (def.getEachItemBodySetting().getValue() != 0) settings.setEachItemBodySetting(def.getEachItemBodySetting().getValue());
        if (null != def.getEachItemBodyTemplate()) settings.setEachItemBodyTemplate(def.getEachItemBodyTemplate());

        if (def.getEntireListIndex()) settings.setEntireListIndex(def.getEntireListIndex());
        if (def.getEntireListIndexSetting().getValue() != 0) settings.setEntireListIndexSetting(def.getEntireListIndexSetting().getValue());
        if (def.getEntireListTitleSetting().getValue() != 0) settings.setEntireListTitleSetting(def.getEntireListTitleSetting().getValue());
        if (null != def.getEntireListTitleTemplate()) settings.setEntireListTitleTemplate(def.getEntireListTitleTemplate());
        if (def.getEntireListBodySetting().getValue() != 0) settings.setEntireListBodySetting(def.getEntireListBodySetting().getValue());
        if (null != def.getEntireListBodyTemplate()) settings.setEntireListBodyTemplate(def.getEntireListBodyTemplate());

        if (def.getFileAttachmentIndex()) settings.setFileAttachmentIndex(def.getFileAttachmentIndex());
    }

    private void writeAttachments(TableInfo ti, ListDefinition def, Container c, VirtualFile listsDir, PHI exportPhiLevel) throws SQLException, IOException
    {
        List<ColumnInfo> attachmentColumns = new ArrayList<>();

        for (DomainProperty prop : def.getDomain().getProperties())
        {
            if (prop.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
            {
                ColumnInfo column = ti.getColumn(prop.getName());

                // Don't export columns at or above the export PHI level if PHI columns are being skipped
                if (shouldRemovePhi(exportPhiLevel, column))
                    continue;

                attachmentColumns.add(column);
            }
        }

        if (!attachmentColumns.isEmpty())
        {
            VirtualFile listDir = listsDir.getDir(def.getName());
            Map<String, FileNameUniquifier> uniquifiers = new HashMap<>();

            for (ColumnInfo attachmentColumn : attachmentColumns)
                uniquifiers.put(attachmentColumn.getName(), new FileNameUniquifier());

            List<ColumnInfo> selectColumns = new ArrayList<>(attachmentColumns);
            selectColumns.add(0, ti.getColumn("EntityId"));

            try (ResultSet rs = QueryService.get().select(ti, selectColumns, null, null))
            {
                while (rs.next())
                {
                    String entityId = rs.getString(1);
                    AttachmentParent listItemParent = new ListItemAttachmentParent(entityId, c);

                    rs.getString(1);
                    int sqlColumn = 2;

                    for (ColumnInfo attachmentColumn : attachmentColumns)
                    {
                        String filename = rs.getString(sqlColumn++);

                        // Item might not have an attachment in this column
                        if (null == filename)
                            continue;

                        String columnName = attachmentColumn.getName();
                        VirtualFile columnDir = listDir.getDir(columnName);
                        FileNameUniquifier uniquifier = uniquifiers.get(columnName);

                        try (InputStream is = AttachmentService.get().getInputStream(listItemParent, filename); OutputStream os = columnDir.getOutputStream(uniquifier.uniquify(filename)))
                        {
                            FileUtil.copyData(is, os);
                        }
                        catch (FileNotFoundException e)
                        {
                            // Shouldn't happen... but just skip this file in production if it does
                            assert false;
                        }
                    }
                }
            }
        }
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, boolean metaData, PHI exportPhiLevel)
    {
        Collection<ColumnInfo> columns = new LinkedHashSet<>();
        Set<ColumnInfo> pks = new HashSet<>(tinfo.getPkColumns());

        assert pks.size() == 1;

        for (ColumnInfo column : tinfo.getColumns())
        {
            /*
                We export:

                - All user-editable columns (meta data & values)
                - All primary keys, including the values of auto-increment key columns (meta data & values)
                - Other key columns (meta data only)
             */
            if ((column.isUserEditable() || pks.contains(column) || (metaData && column.isKeyField())))
            {
                // Exclude columns marked above the export PHI level (except key columns marked at or above the export PHI level, those must be exported)
                if (shouldRemovePhi(exportPhiLevel, column) && !pks.contains(column) && !column.isKeyField())
                    continue;

                columns.add(column);

                // If the column is MV enabled, export the data in the indicator column as well
                if (!metaData && column.isMvEnabled())
                {
                    ColumnInfo mvIndicator = tinfo.getColumn(column.getMvColumnName());

                    if (null == mvIndicator)
                        ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("MV indicator column not found: " + tinfo.getName() + "|" + column.getMvColumnName()));
                    else
                        columns.add(mvIndicator);
                }
            }
        }

        return columns;
    }

    private static boolean shouldRemovePhi(PHI exportPhiLevel, ColumnInfo column)
    {
        return !column.getPHI().isExportLevelAllowed(exportPhiLevel);
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

            // should not include if it is above PHI export level
            assertTrue(shouldRemovePhi(PHI.PHI, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, ciPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, ciPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, ciLimitedPhi));

            // should include if it is at or above PHI export level
            assertFalse(shouldRemovePhi(PHI.PHI, ciPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.Restricted, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, ciNotPhi));
        }
    }

    // We just want the underlying value, not the lookup
    private static class ListExportDataColumn extends DataColumn
    {
        private ListExportDataColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }
    }

    private static class ListTableInfoWriter extends TableInfoWriter
    {
        private final ListDefinition _def;
        private final Map<String, DomainProperty> _properties = new HashMap<>();
        private final Domain _domain;

        protected ListTableInfoWriter(TableInfo ti, ListDefinition def, Collection<ColumnInfo> columns)
        {
            super(def.getContainer(), ti, columns);
            _def = def;
            _domain = _def.getDomain();

            for (DomainProperty prop : _domain.getProperties())
                _properties.put(prop.getName(), prop);
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);
            tableXml.setPkColumnName(_def.getKeyName());
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            String columnName = column.getName();

            if (columnName.equals(_def.getKeyName()))
            {
                columnXml.setIsKeyField(true);

                if (column.isAutoIncrement())
                    columnXml.setIsAutoInc(true);
            }
            else
            {
                DomainProperty domainProperty = _properties.get(columnName);
                if (null != domainProperty)             // #25367: it's possible properties are out of sync with actual columns
                {
                    PropertyDescriptor propertyDescriptor = domainProperty.getPropertyDescriptor();

                    // Write URL, if exists, from property descriptor
                    if (null != propertyDescriptor.getURL())
                        columnXml.setUrl(propertyDescriptor.getURL().toXML());
                }
            }
        }

        @Override  // No reason to ever export list PropertyURIs, see #18718
        protected String getPropertyURI(ColumnInfo column)
        {
            return null;
        }
    }
}
