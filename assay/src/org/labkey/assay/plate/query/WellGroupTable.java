/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.assay.plate.PlateCache;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class WellGroupTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "WellGroup";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    boolean _allowInsertUpdateDelete;

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("PlateId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Name"));
        defaultVisibleColumns.add(FieldKey.fromParts("TypeName"));
    }

    public WellGroupTable(PlateSchema schema, ContainerFilter cf, boolean allowInsertUpdateDelete)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWellGroup(), cf);
        _allowInsertUpdateDelete = allowInsertUpdateDelete;
        setTitleColumn("Name");
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addColumn(createPropertiesColumn());
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    private MutableColumnInfo createPropertiesColumn()
    {
        ColumnInfo lsidCol = getColumn("LSID", false);
        var col = new AliasedColumn(this, "Properties", lsidCol);
        col.setDescription("Properties associated with this WellGroup");
        col.setHidden(true);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setCalculated(true);
        col.setIsUnselectable(true);

        String propPrefix = new Lsid("WellGroupTemplate", "Folder-" + getContainer().getRowId(), "objectType#").toString();
        SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
        filter.addCondition(FieldKey.fromParts("PropertyURI"), propPrefix, CompareType.STARTS_WITH);
        final Map<String, PropertyDescriptor> map = new TreeMap<>();

        new TableSelector(OntologyManager.getTinfoPropertyDescriptor(), filter, null).forEach(PropertyDescriptor.class, pd -> {
            if (pd.getPropertyType() == PropertyType.DOUBLE)
                pd.setFormat("0.##");
            map.put(pd.getName(), pd);
        });
        col.setFk(new PropertyForeignKey(getUserSchema(), getContainerFilter(), map));

        return col;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!_allowInsertUpdateDelete && (InsertPermission.class.equals(perm) || UpdatePermission.class.equals(perm) || DeletePermission.class.equals(perm)))
            return false;
        return super.hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new WellGroupUpdateService(this, AssayDbSchema.getInstance().getTableInfoWellGroup());
    }

    protected static class WellGroupUpdateService extends DefaultQueryUpdateService
    {
        public WellGroupUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            final TableInfo wellGroupTable = getQueryTable();

            SimpleTranslator lsidRemover = new SimpleTranslator(data.getDataIterator(context), context);
            lsidRemover.selectAll();
            if (lsidRemover.getColumnNameMap().containsKey("lsid"))
            {
                // remove any furnished lsid since we will be computing one
                lsidRemover.removeColumn(lsidRemover.getColumnNameMap().get("lsid"));
            }

            SimpleTranslator lsidGenerator = new SimpleTranslator(lsidRemover, context);
            lsidGenerator.setDebugName("lsidGenerator");
            lsidGenerator.selectAll();
            final Map<String, Integer> nameMap = lsidGenerator.getColumnNameMap();

            // generate a value for the lsid
            lsidGenerator.addColumn(wellGroupTable.getColumn("lsid"),
                    (Supplier) () -> PlateManager.get().getLsid(WellGroup.class, container));

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(wellGroupTable, lsidGenerator, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, wellGroupTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId", "Lsid"));
            dib = LoggingDataIterator.wrap(dib);
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container);

            return dib;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            try (DbScope.Transaction transaction = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                Integer wellGroupId = (Integer)oldRowMap.get("RowId");
                if (wellGroupId != null)
                {
                    WellGroup wellGroup = PlateManager.get().getWellGroup(container, wellGroupId);
                    if (wellGroup != null)
                    {
                        Plate plate = wellGroup.getPlate();
                        PlateManager.get().beforeDeleteWellGroup(container, wellGroupId);
                        Map<String, Object> returnMap = super.deleteRow(user, container, oldRowMap);

                        if (plate != null)
                            transaction.addCommitTask(() -> PlateCache.uncache(container, plate), DbScope.CommitTaskOption.POSTCOMMIT);
                        transaction.commit();

                        return returnMap;
                    }
                }
                return Collections.emptyMap();
            }
        }
    }
}
