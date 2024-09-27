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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
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
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.GUID;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.labkey.api.query.ExprColumn.STR_TABLE_ALIAS;

public class PlateTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Plate";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private final boolean _allowInsert;
    public static final String PLATE_BARCODE_SEQUENCE = "org.labkey.assay.plate.barcode";

    public enum Column
    {
        Archived,
        AssayType,
        Barcode,
        Container,
        Created,
        CreatedBy,
        Description,
        Lsid,
        Modified,
        ModifiedBy,
        Name,
        PlateId,
        PlateSet,
        PlateType,
        Properties,
        RowId,
        Template,
        WellsFilled;

        public FieldKey fieldKey()
        {
            return FieldKey.fromParts(name());
        }
    }

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Name.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Barcode.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Description.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.PlateType.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.PlateSet.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.AssayType.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.WellsFilled.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Created.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.CreatedBy.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Modified.name()));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.ModifiedBy.name()));
    }

    public PlateTable(PlateSchema schema, @Nullable ContainerFilter cf, boolean allowInsert)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoPlate(), cf);
        _allowInsert = allowInsert;
        setTitleColumn(Column.Name.name());
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addColumn(createPropertiesColumn());
        addWellsFilledColumn();
    }

    @Override
    protected void fixupWrappedColumn(MutableColumnInfo wrap, ColumnInfo col)
    {
        super.fixupWrappedColumn(wrap, col);

        if (Column.Container.name().equalsIgnoreCase(col.getName()))
        {
            wrap.setFieldKey(FieldKey.fromParts("Folder"));
            wrap.setLabel(getContainer().hasProductProjects() ? "Project" : "Folder");
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    private MutableColumnInfo createPropertiesColumn()
    {
        ColumnInfo lsidCol = getColumn(Column.Lsid.name(), false);
        var col = new AliasedColumn(this, Column.Properties.name(), lsidCol);
        col.setDescription("Properties associated with this Plate");
        col.setHidden(true);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setCalculated(true);
        col.setIsUnselectable(true);

        String propPrefix = new Lsid("PlateTemplate", "Folder-" + getContainer().getRowId(), "objectType#").toString();
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

    private void addWellsFilledColumn()
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(*) AS wellsFilled FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoWell(), "")
                .append(" WHERE PlateId = " + STR_TABLE_ALIAS + ".RowId")
                .append(" AND sampleId IS NOT NULL)");
        ExprColumn countCol = new ExprColumn(this, Column.WellsFilled.name(), sql, JdbcType.INTEGER);
        countCol.setDescription("The number of wells that have samples for this plate.");
        addColumn(countCol);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!_allowInsert && InsertPermission.class.equals(perm))
            return false;
        return super.hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new PlateUpdateService(this, AssayDbSchema.getInstance().getTableInfoPlate());
    }

    protected static class PlateUpdateService extends DefaultQueryUpdateService
    {
        public PlateUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            SimpleTranslator lsidRemover = new SimpleTranslator(data.getDataIterator(context), context);
            lsidRemover.selectAll();
            if (lsidRemover.getColumnNameMap().containsKey(Column.Lsid.name()))
            {
                // remove any furnished lsid since we will be computing one
                lsidRemover.removeColumn(lsidRemover.getColumnNameMap().get(Column.Lsid.name()));
            }

            SimpleTranslator lsidGenerator = new SimpleTranslator(lsidRemover, context);
            lsidGenerator.setDebugName("lsidGenerator");
            lsidGenerator.selectAll();
            final Map<String, Integer> nameMap = lsidGenerator.getColumnNameMap();

            if (!nameMap.containsKey(Column.Template.name()))
            {
                context.getErrors().addRowError(new ValidationException(String.format("%s is a required field", Column.Template.name())));
                return data;
            }

            if (!nameMap.containsKey(Column.PlateSet.name()))
            {
                context.getErrors().addRowError(new ValidationException("Plate set is a required field"));
                return data;
            }

            final TableInfo plateTable = getQueryTable();

            // generate a value for the lsid
            lsidGenerator.addColumn(plateTable.getColumn(Column.Lsid.name()),
                    (Supplier) () -> PlateManager.get().getLsid(Plate.class, container));

            // generate the data file id if not provided
            if (!nameMap.containsKey("dataFileId"))
            {
                lsidGenerator.addColumn(plateTable.getColumn("dataFileId"),
                        (Supplier) () -> GUID.makeGUID());
            }

            SimpleTranslator nameExpressionTranslator = new SimpleTranslator(lsidGenerator, context);
            nameExpressionTranslator.setDebugName("nameExpressionTranslator");
            nameExpressionTranslator.selectAll();
            if (!nameMap.containsKey(Column.Name.name()))
            {
                ColumnInfo nameCol = plateTable.getColumn(Column.Name.name());
                nameExpressionTranslator.addColumn(nameCol, (Supplier) () -> null);
            }

            if (!nameMap.containsKey(Column.PlateId.name()))
            {
                ColumnInfo nameCol = plateTable.getColumn(Column.PlateId.name());
                nameExpressionTranslator.addColumn(nameCol, (Supplier) () -> null);
            }

            // Add generated barcode column for use in BarcodeDataIterator
            String barcodeGeneratedName = "barcodeGenerated";
            ColumnInfo genIdCol = new BaseColumnInfo(FieldKey.fromParts(barcodeGeneratedName), JdbcType.VARCHAR);
            nameExpressionTranslator.addTextSequenceColumn(genIdCol, genIdCol.getDbSequenceContainer(ContainerManager.getRoot()), PLATE_BARCODE_SEQUENCE, null, 100);

            DataIterator builtInColumnsTranslator = SimpleTranslator.wrapBuiltInColumns(nameExpressionTranslator, context, container, user, plateTable);
            DataIterator di = LoggingDataIterator.wrap(new NamePlusIdDataIterator(builtInColumnsTranslator, context, plateTable,
                    container,
                    "name",
                    "plateId",
                    PlateManager.get().getPlateNameExpression()));

            di = LoggingDataIterator.wrap(new BarcodeDataIterator(di, Column.Barcode.name(), barcodeGeneratedName, Column.Template.name()));
            di = LoggingDataIterator.wrap(new DuplicatePlateValidator(di, context, container, user));

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(plateTable, di, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, plateTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet(Column.RowId.name(), Column.Lsid.name()));
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
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, @Nullable Map<Enum, Object> configParameters) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Integer plateId = (Integer) oldRow.get(Column.RowId.name());
            Plate plate = PlateManager.get().getPlate(container, plateId);
            if (plate == null)
                return Collections.emptyMap();

            // During upgrades we do not want to enforce this plate edit constraint.
            if (!User.getAdminServiceUser().equals(user))
            {
                int runsInUse = PlateManager.get().getRunCountUsingPlate(container, user, plate);
                if (runsInUse > 0)
                    throw new QueryUpdateServiceException(String.format("%s is used by %d runs and cannot be updated", plate.isTemplate() ? "Plate template" : "Plate", runsInUse));
            }

            // disallow updates of certain columns
            preventUpdates(row, oldRow, Column.AssayType, Column.PlateSet, Column.PlateType);

            // if the name is changing, check for duplicates
            if (row.containsKey(Column.Name.name()))
            {
                String oldName = (String) oldRow.get(Column.Name.name());
                String newName = StringUtils.trimToNull((String) row.get(Column.Name.name()));
                if (newName != null && !newName.equals(oldName))
                {
                    if (plate.isTemplate() && PlateManager.get().isDuplicatePlateTemplateName(container, newName))
                        throw new QueryUpdateServiceException(String.format("Plate template with name \"%s\" already exists.", newName));
                    if (PlateManager.get().isDuplicatePlateName(container, user, newName, plate.getPlateSet()))
                        throw new QueryUpdateServiceException("Plate with name : " + newName + " already exists.");
                }

                // Do not allow empty string as the name. Fallback to PlateId.
                if (newName == null)
                    row.put(Column.Name.name(), plate.getPlateId());
            }

            Map<String, Object> newRow = super.updateRow(user, container, row, oldRow, configParameters);
            PlateManager.get().clearCache(container, plate);
            return newRow;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            Integer plateId = (Integer) oldRowMap.get(Column.RowId.name());
            Plate plate = PlateManager.get().getPlate(container, plateId);
            if (plate == null)
                return Collections.emptyMap();

            int runsInUse = PlateManager.get().getRunCountUsingPlate(container, user, plate);
            if (runsInUse > 0)
                throw new QueryUpdateServiceException(String.format("%s is used by %d runs and cannot be deleted", plate.isTemplate() ? "Plate template" : "Plate", runsInUse));

            try (DbScope.Transaction transaction = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                PlateManager.get().beforePlateDelete(container, plateId);
                Map<String, Object> returnMap = super.deleteRow(user, container, oldRowMap);

                transaction.addCommitTask(() -> PlateManager.get().afterPlateDelete(container, plate), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();

                return returnMap;
            }
        }

        private void preventUpdates(Map<String, Object> newRow, Map<String, Object> oldRow, Column... columns) throws QueryUpdateServiceException
        {
            for (Column column : columns)
            {
                String columnName = column.name();
                if (newRow.containsKey(columnName) && ObjectUtils.notEqual(oldRow.get(columnName), newRow.get(columnName)))
                    throw new QueryUpdateServiceException(String.format("Updating \"%s\" is not allowed.", columnName));
            }
        }
    }
}
