package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetCache;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.labkey.api.query.ExprColumn.STR_TABLE_ALIAS;

public class PlateSetTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "PlateSet";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private final boolean _allowInsert;

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("Name"));
        defaultVisibleColumns.add(FieldKey.fromParts("Folder"));
        defaultVisibleColumns.add(FieldKey.fromParts("Type"));
        defaultVisibleColumns.add(FieldKey.fromParts("Description"));
        defaultVisibleColumns.add(FieldKey.fromParts("PlateCount"));
        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("Modified"));
        defaultVisibleColumns.add(FieldKey.fromParts("ModifiedBy"));
    }

    public PlateSetTable(PlateSchema schema, @Nullable ContainerFilter cf, boolean allowInsert)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoPlateSet(), cf);
        _allowInsert = allowInsert;
        setTitleColumn("Name");
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addPlateCountColumn();
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo col)
    {
        var columnInfo = super.wrapColumn(col);

        if (columnInfo.getName().equalsIgnoreCase("RowId"))
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
            columnInfo.setSortDirection(Sort.SortDirection.DESC);
        }
        return columnInfo;
    }

    @Override
    protected void fixupWrappedColumn(MutableColumnInfo wrap, ColumnInfo col)
    {
        super.fixupWrappedColumn(wrap, col);

        if ("Container".equalsIgnoreCase(col.getName()))
        {
            wrap.setFieldKey(FieldKey.fromParts("Folder"));
            wrap.setLabel(getContainer().hasProductProjects() ? "Project" : "Folder");
        }
    }

    private void addPlateCountColumn()
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(*) AS plateCount FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoPlate(), "PT")
                .append(" WHERE PT.PlateSet = " + STR_TABLE_ALIAS + ".RowId)");
        ExprColumn countCol = new ExprColumn(this, "PlateCount", sql, JdbcType.INTEGER);
        countCol.setDescription("The number of plates that are assigned to this plate set");
        addColumn(countCol);
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
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
            if (!nameMap.containsKey("plateSetId"))
            {
                ColumnInfo nameCol = plateSetTable.getColumn("plateSetId");
                nameExpressionTranslator.addColumn(nameCol, (Supplier) () -> null);
            }
            DataIterator builtInColumnsTranslator = SimpleTranslator.wrapBuiltInColumns(nameExpressionTranslator, context, container, user, plateSetTable);

            DataIterator di = LoggingDataIterator.wrap(new NamePlusIdDataIterator(builtInColumnsTranslator, context, plateSetTable,
                    container,
                    "name",
                    "plateSetId",
                    PlateManager.get().getPlateSetNameExpression()));
            DataIteratorBuilder insertBuilder = LoggingDataIterator.wrap(StandardDataIteratorBuilder.forInsert(getDbTable(), di, container, user, context));
            DataIteratorBuilder dib = new TableInsertDataIteratorBuilder(insertBuilder, plateSetTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId"));
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(plateSetTable, dib, context.getInsertOption(), user, container);

            return dib;
        }

        @Override
        public List<Map<String, Object>> insertRows(
            User user,
            Container container,
            List<Map<String, Object>> rows,
            BatchValidationException errors,
            @Nullable Map<Enum, Object> configParameters,
            Map<String, Object> extraScriptContext
        )
        {
            return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        protected Map<String, Object> deleteRow(
            User user,
            Container container,
            Map<String, Object> oldRowMap
        ) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            // ensure the plate set is empty
            Integer plateSetId = (Integer) oldRowMap.get("RowId");
            PlateSet plateSet = PlateManager.get().getPlateSet(container, plateSetId);
            if (plateSet == null)
                throw new QueryUpdateServiceException(String.format("Plate set could not be found for ID : %d", plateSetId));

            List<Plate> plates = plateSet.getPlates();
            if (!plates.isEmpty())
                throw new QueryUpdateServiceException(String.format("Plate set has %d plates associated with it and cannot be deleted.", plates.size()));

            try (DbScope.Transaction transaction = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                PlateManager.get().beforePlateSetDelete(container, user, (Integer) oldRowMap.get("RowId"));

                Map<String, Object> returnMap = super.deleteRow(user, container, oldRowMap);

                transaction.addCommitTask(() -> PlateSetCache.uncache(container, plateSet), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
                return returnMap;
            }
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, @Nullable Map<Enum, Object> configParameters) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Integer plateSetId = (Integer) oldRow.get("rowId");
            PlateSet plateSet = PlateManager.get().requirePlateSet(container, plateSetId, "Failed to update plate set.");

            try (DbScope.Transaction transaction = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                Map<String, Object> newRow = super.updateRow(user, container, row, oldRow, configParameters);
                transaction.addCommitTask(() -> PlateSetCache.uncache(container, plateSet), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
                return newRow;
            }
        }
    }
}
