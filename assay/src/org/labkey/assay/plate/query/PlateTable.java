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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.NameExpressionDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.dataiterator.ValidatorIterator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.ValidatorContext;
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
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
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

public class PlateTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Plate";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("Name"));
        defaultVisibleColumns.add(FieldKey.fromParts("Rows"));
        defaultVisibleColumns.add(FieldKey.fromParts("Columns"));
        defaultVisibleColumns.add(FieldKey.fromParts("Type"));
        defaultVisibleColumns.add(FieldKey.fromParts("PlateTypeId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("Modified"));
        defaultVisibleColumns.add(FieldKey.fromParts("ModifiedBy"));
    }

    public PlateTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoPlate(), cf);
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
            final TableInfo plateTable = getQueryTable();

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

            if (!nameMap.containsKey("template"))
            {
                context.getErrors().addRowError(new ValidationException("Template is a required field"));
                return data;
            }

            if (!nameMap.containsKey("plateSet"))
            {
                context.getErrors().addRowError(new ValidationException("Plate set is a required field"));
                return data;
            }

            // generate a value for the lsid
            lsidGenerator.addColumn(plateTable.getColumn("lsid"),
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
            if (!nameMap.containsKey("name"))
            {
                ColumnInfo nameCol = plateTable.getColumn("name");
                nameExpressionTranslator.addColumn(nameCol, (Supplier) () -> null);
            }

            nameExpressionTranslator.addColumn(new BaseColumnInfo("nameExpression", JdbcType.VARCHAR),
                    (Supplier) () -> PlateService.get().getPlateNameExpression());
            DataIterator builtInColumnsTranslator = SimpleTranslator.wrapBuiltInColumns(nameExpressionTranslator, context, container, user, plateTable);
            DataIterator di = LoggingDataIterator.wrap(new NameExpressionDataIterator(builtInColumnsTranslator, context, plateTable, container, null, null, null));

            ValidatorIterator vi = new ValidatorIterator(di, context, container, user);
            vi.addValidator(nameMap.get("name"), new UniquePlateNameValidator(container));

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(plateTable, vi, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, plateTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId", "Lsid"));
            dib = LoggingDataIterator.wrap(dib);
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container);

            return dib;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            List<Map<String, Object>> results = super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
            return results;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Integer plateId = (Integer) oldRow.get("RowId");
            Plate plate = PlateManager.get().getPlate(container, plateId);
            if (plate == null)
                return Collections.emptyMap();

            int runsInUse = PlateManager.get().getRunCountUsingPlate(container, user, plate);
            if (runsInUse > 0)
                throw new QueryUpdateServiceException(String.format("%s is used by %d runs and cannot be updated", plate.isTemplate() ? "Plate template" : "Plate", runsInUse));

            // disallow plate size changes
            if ((row.containsKey("rows") && ObjectUtils.notEqual(oldRow.get("rows"), row.get("rows"))) ||
                    (row.containsKey("columns") && ObjectUtils.notEqual(oldRow.get("columns"), row.get("columns"))))
            {
                throw new QueryUpdateServiceException("Changing the plate size (rows or columns) is not allowed.");
            }

            // if the name is changing, check for duplicates
            String oldName = (String) oldRow.get("Name");
            String newName = (String) row.get("Name");
            if (!newName.equals(oldName))
            {
                if (PlateManager.get().plateExists(container, newName))
                    throw new QueryUpdateServiceException("Plate with name : " + newName + " already exists in the folder.");
            }

            Map<String, Object> newRow = super.updateRow(user, container, row, oldRow);
            PlateManager.get().clearCache(container, plate);
            return newRow;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            Integer plateId = (Integer)oldRowMap.get("RowId");
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
    }

    private static class UniquePlateNameValidator implements ColumnValidator
    {
        private Container _container;

        public UniquePlateNameValidator(Container container)
        {
            _container = container;
        }

        @Override
        public String validate(int rowNum, Object value)
        {
            return validate(String.valueOf(value));
        }

        @Override
        public String validate(int rowNum, Object value, ValidatorContext validatorContext)
        {
            return validate(String.valueOf(value));
        }

        private String validate(String name)
        {
            if (PlateManager.get().plateExists(_container, name))
                return "Plate with name : " + name + " already exists in the folder.";
            else
                return null;
        }
    }
}
