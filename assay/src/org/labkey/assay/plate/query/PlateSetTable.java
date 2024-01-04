package org.labkey.assay.plate.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.NameExpressionDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PlateSetTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "PlateSet";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("Name"));
        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("Archived"));
    }

    public PlateSetTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoPlateSet(), cf);
        setTitleColumn("Name");
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo col)
    {
        var columnInfo = super.wrapColumn(col);

        // the name field is always generated via name expression
        if (columnInfo.getName().equalsIgnoreCase("Name"))
        {
            columnInfo.setUserEditable(false);
        }
        else if (columnInfo.getName().equalsIgnoreCase("RowId"))
        {
            // this is necessary in order to use rowId as a name expression token
            columnInfo.setKeyField(true);
            columnInfo.setUserEditable(false);
            columnInfo.setHidden(true);
            columnInfo.setShownInInsertView(false);
            columnInfo.setShownInUpdateView(false);
            columnInfo.setFk(new RowIdForeignKey(columnInfo));
            columnInfo.setHasDbSequence(true);
            columnInfo.setDbSequenceBatchSize(1);
            columnInfo.setIsRootDbSequence(true);
        }
        return columnInfo;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new PlateSetUpdateService(this, AssayDbSchema.getInstance().getTableInfoPlateSet());
    }

    protected static class PlateSetUpdateService extends DefaultQueryUpdateService
    {
        public PlateSetUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            SimpleTranslator nameExpressionTranslator = new SimpleTranslator(data.getDataIterator(context), context);
            nameExpressionTranslator.setDebugName("nameExpressionTranslator");
            nameExpressionTranslator.selectAll();
            final Map<String, Integer> nameMap = nameExpressionTranslator.getColumnNameMap();
            final TableInfo plateSetTable = getQueryTable();
            if (!nameMap.containsKey("name"))
            {
                ColumnInfo nameCol = plateSetTable.getColumn("name");
                nameExpressionTranslator.addColumn(nameCol, (Supplier) () -> null);
            }
            nameExpressionTranslator.addColumn(new BaseColumnInfo("nameExpression", JdbcType.VARCHAR),
                    (Supplier) () -> PlateService.get().getPlateSetNameExpression());
            DataIterator builtInColumnsTranslator = SimpleTranslator.wrapBuiltInColumns(nameExpressionTranslator, context, container, user, plateSetTable);
            DataIterator di = LoggingDataIterator.wrap(new NameExpressionDataIterator(builtInColumnsTranslator, context, plateSetTable, container, null, null, null));

            DataIteratorBuilder insertBuilder = LoggingDataIterator.wrap(StandardDataIteratorBuilder.forInsert(getDbTable(), di, container, user, context));
            DataIteratorBuilder dib = new TableInsertDataIteratorBuilder(insertBuilder, plateSetTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId"));
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(plateSetTable, dib, context.getInsertOption(), user, container);

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
            // ensure the plate set is empty
            Integer plateSetId = (Integer)oldRowMap.get("RowId");
            PlateSet plateSet = PlateManager.get().getPlateSet(container, plateSetId);
            if (plateSet == null)
                throw new QueryUpdateServiceException(String.format("Plate set could not be found for ID : %d", plateSetId));

            List<Plate> plates = plateSet.getPlates(user);
            if (!plates.isEmpty())
                throw new QueryUpdateServiceException(String.format("Plate set has %d plates associated with it and cannot be deleted.", plates.size()));

            try (DbScope.Transaction transaction = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                Map<String, Object> returnMap = super.deleteRow(user, container, oldRowMap);

                transaction.commit();
                return returnMap;
            }
        }
    }
}
