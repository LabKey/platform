package org.labkey.assay.plate.query;

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WellTable extends SimpleUserSchema.SimpleTable<PlateSchema>
{
    public static final String NAME = "Well";
    public static final String WELL_PROPERTIES_TABLE = "WellProperties";

    public static final String PLATEID_COL = "PlateId";
    public static final String ROW_COL = "Row";
    public static final String COL_COL = "Col";
    public static final String POSITION_COL = "Position";

    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private static final Set<String> ignoredColumns = new CaseInsensitiveHashSet();
    private final Map<FieldKey, ColumnInfo> _provisionedFieldMap = new HashMap<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("PlateId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Row"));
        defaultVisibleColumns.add(FieldKey.fromParts("Col"));
        defaultVisibleColumns.add(FieldKey.fromParts("Position"));

        // for now don't surface value and dilution, we may choose to drop these fields from the
        // db schema at some point
        ignoredColumns.add("value");
        ignoredColumns.add("dilution");
    }

    public WellTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWell(), cf);

        addTriggerFactory((c, self, extraContext) -> List.of(new WellTableTrigger()));
    }

    @Override
    public void addColumns()
    {
        super.addColumns();

        SQLFragment positionSql = new SQLFragment();
        positionSql.append("(CASE");
        for (int i=0; i < PositionImpl.ALPHABET.length; i++)
        {
            positionSql.append("\n")
                    .append("WHEN ").append(ExprColumn.STR_TABLE_ALIAS).append(getSqlDialect().concatenate(".Row = ? THEN (?", "CAST("))
                    .append(ExprColumn.STR_TABLE_ALIAS).append(".Col + 1 AS VARCHAR))")
                    .add(i)
                    .add(PositionImpl.ALPHABET[i]);
        }
        positionSql.append(" END)");
        var positionCol = new ExprColumn(this, "Position", positionSql, JdbcType.VARCHAR);
        positionCol.setSortFieldKeys(List.of(FieldKey.fromParts("RowId")));
        positionCol.setUserEditable(false);
        addColumn(positionCol);

        addWellProperties();
    }

    /**
     * Adds a FK to the provisioned properties table, this is done in order to expose the fields in a similar
     * way that vocabulary domain properties were exposed in the table.
     */
    private void addWellProperties()
    {
        Domain domain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUserSchema().getUser());
        ColumnInfo lsidCol = getColumn("Lsid");
        if (domain != null && lsidCol != null)
        {
            BaseColumnInfo col = new AliasedColumn("Properties", lsidCol);
            col.setFk(QueryForeignKey
                    .from(getUserSchema(), getContainerFilter())
                    .to(WELL_PROPERTIES_TABLE, "Lsid", null)
            );
            col.setLabel("Plate Metadata");
            col.setDescription("Custom properties associated with the plate well");
            col.setUserEditable(false);
            col.setCalculated(true);
            addColumn(col);

            // add fields from the virtual well properties table
            TableInfo tableInfo = getUserSchema().getTable(WELL_PROPERTIES_TABLE);
            if (tableInfo != null)
            {
                for (var column : tableInfo.getColumns())
                {
                    if (column.getName().equalsIgnoreCase("lsid"))
                        continue;

                    FieldKey fieldKey = FieldKey.fromParts("Properties", column.getName());
                    defaultVisibleColumns.add(fieldKey);
                    _provisionedFieldMap.put(fieldKey, column);
                }
            }
        }
    }

    /**
     * Alternatively, if we wish to join the provisioned fields into the well table without introducing another hierarchy
     * level we just wrap the fields. This also requires the code : getFromSql to be uncommented to handle the join.
     */
    private void addWellPropertiesFlattened()
    {
        Domain wellDomain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUserSchema().getUser());
        FieldKey lsidFieldKey = FieldKey.fromParts("lsid");
        Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;

        TableInfo metadataTable = PlateManager.get().getPlateMetadataTable(getContainer(), _userSchema.getUser());
        if (metadataTable != null)
        {
            for (ColumnInfo col : metadataTable.getColumns())
            {
                if (col.getFieldKey().equals(lsidFieldKey))
                    continue;

                var wrapped = wrapColumnFromJoinedTable(col.getName(), col);
                if (col.isHidden())
                    wrapped.setHidden(true);

                // Copy the property descriptor settings to the wrapped column.
                String propertyURI = col.getPropertyURI();
                DomainProperty dp = propertyURI != null ? wellDomain.getPropertyByURI(propertyURI) : null;
                PropertyDescriptor pd = (null == dp) ? null : dp.getPropertyDescriptor();
                if (dp != null && pd != null)
                    defaultsSupplier = PropertyColumn.copyAttributes(getUserSchema().getUser(), wrapped, dp, getContainer(), lsidFieldKey, getContainerFilter(), defaultsSupplier);

                addColumn(wrapped);
                defaultVisibleColumns.add(col.getFieldKey());
            }
        }
    }

    @Override
    protected boolean acceptColumn(ColumnInfo col)
    {
        return super.acceptColumn(col) && !ignoredColumns.contains(col.getName());
    }

    /**
     * Override to resolve Properties/name FieldKeys for the well properties columns.
     */
    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        FieldKey fieldKey = FieldKey.decode(name);
        if (_provisionedFieldMap.containsKey(fieldKey))
        {
            return _provisionedFieldMap.get(fieldKey);
        }
        return super.resolveColumn(name);
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo col)
    {
        var columnInfo = super.wrapColumn(col);

        // workaround for sample lookup not resolving correctly
        if (columnInfo.getName().equalsIgnoreCase("SampleId"))
        {
            columnInfo.setFk(QueryForeignKey.from(getUserSchema(), getContainerFilter())
                    .schema("exp", getContainer())
                    .to("Materials", "RowId", "Name"));
        }
        return columnInfo;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static Set<FieldKey> getMetadataColumns(int plateSetId, Container c, User u) throws ValidationException
    {
        PlateSet plateSet = PlateManager.get().getPlateSet(c, plateSetId);
        if (plateSet == null)
            throw new ValidationException("Unable to resolve plate set of id " + plateSetId);

        List<Plate> plates = plateSet.getPlates(u);

        Set<FieldKey> includedMetadataCols = new HashSet<>();
        for (Plate plate : plates)
        {
            List<String> metadataColNames = PlateManager.get().getFields(c, plate.getRowId()).stream().map(PlateCustomField::getName).collect(Collectors.toCollection(ArrayList::new));
            for (String name : metadataColNames)
                includedMetadataCols.add(FieldKey.fromParts("properties", name));
        }

        return includedMetadataCols;
    }

/*
    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return getFromSQL(alias, null);
    }

    @Override
    public SQLFragment getFromSQL(String alias, Set<FieldKey> selectedColumns)
    {
        TableInfo wellProperties = PlateManager.get().getPlateMetadataTable(getContainer(), _userSchema.getUser());
        // join the base assay.well table to the provisioned table
        checkReadBeforeExecute();

        Set<String> baseColumns = new CaseInsensitiveHashSet(_rootTable.getColumnNameSet());

        // all columns from provisioned table except lsid
        Set<String> provisionedColumns = new CaseInsensitiveHashSet(wellProperties.getColumnNameSet());
        provisionedColumns.remove("lsid");

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT * FROM (SELECT ");
        String delim = "";
        for (String col : baseColumns)
        {
            sql.append(delim).append("d.").append(col);
            delim = ", ";
        }

        for (String col : provisionedColumns)
        {
            sql.append(delim).append(wellProperties.getColumn(col).getValueSql("p"));
        }

        sql.append(" FROM ")
                .append(_rootTable, "d")
                .append(" INNER JOIN ")
                .append(wellProperties, "p").append(" ON d.lsid = p.lsid");
        String subAlias = alias + "_wp_sub";
        sql.append(") ").append(subAlias);
        sql.append("\n");

        // add the WHERE clause
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), subAlias, columnMap);
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return sql;
    }
*/

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo provisionedTable = null;
        Domain domain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUserSchema().getUser());
        if (domain != null)
            provisionedTable = StorageProvisioner.createTableInfo(domain);

        return new WellUpdateService(this, AssayDbSchema.getInstance().getTableInfoWell(), provisionedTable);
    }

    /**
     * Virtual table which wraps the well properties provisioned table
     */
    protected static class WellPropertiesTable extends FilteredTable<PlateSchema>
    {
        public WellPropertiesTable(@NotNull Domain domain, @NotNull PlateSchema schema, @Nullable ContainerFilter cf)
        {
            super(StorageProvisioner.createTableInfo(domain), schema, cf);
            Domain wellDomain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUserSchema().getUser());
            Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;

            for (ColumnInfo col : getRealTable().getColumns())
            {
                var wrappedCol = wrapColumn(col);
                if (col.getName().equals("Lsid"))
                {
                    wrappedCol.setHidden(true);
                    wrappedCol.setKeyField(true);
                    wrappedCol.setUserEditable(false);
                    wrappedCol.setShownInUpdateView(false);
                    wrappedCol.setShownInInsertView(false);
                }

                // copy the property descriptor settings to the wrapped column
                DomainProperty dp = wellDomain.getPropertyByName(col.getName());
                PropertyDescriptor pd = (null == dp) ? null : dp.getPropertyDescriptor();
                if (dp != null && pd != null)
                {
                    defaultsSupplier = PropertyColumn.copyAttributes(getUserSchema().getUser(), wrappedCol, dp, getContainer(), null, getContainerFilter(), defaultsSupplier);
                    wrappedCol.setFieldKey(FieldKey.fromParts(dp.getName()));
                }
                addColumn(wrappedCol);
            }
        }

        @Override
        public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
        {
            return _userSchema.getContainer().hasPermission(user, perm);
        }
    }

    protected static class WellUpdateService extends DefaultQueryUpdateService
    {
        private final TableInfo _provisionedTable;

        public WellUpdateService(TableInfo queryTable, TableInfo dbTable, TableInfo provisionedTable)
        {
            super(queryTable, dbTable);
            _provisionedTable = provisionedTable;
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            final TableInfo wellTable = getQueryTable();

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

            // consider enforcing this in the schema
            if (!nameMap.containsKey("row") || !nameMap.containsKey("col"))
            {
                context.getErrors().addRowError(new ValidationException("Row and Col are required fields"));
                return data;
            }

            // generate a value for the lsid
            lsidGenerator.addColumn(wellTable.getColumn("lsid"),
                    (Supplier) () -> {
                        Object row = lsidGenerator.get(nameMap.get("row"));
                        Object col = lsidGenerator.get(nameMap.get("col"));

                        Lsid lsid = PlateManager.get().getLsid(Well.class, container);
                        return String.format("%s-well-%s-%s", lsid.toString(), row, col);
                    });

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(wellTable, lsidGenerator, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, wellTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId", "Lsid"));
            if (_provisionedTable != null)
            {
                dib = new TableInsertDataIteratorBuilder(dib, _provisionedTable, container)
                        .setKeyColumns(new CaseInsensitiveHashSet("Lsid"));
            }
            dib = LoggingDataIterator.wrap(dib);
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(wellTable, dib, context.getInsertOption(), user, container);

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
            // enforce no updates if the plate has been imported in an assay run
            if (oldRow.containsKey("plateId"))
            {
                Plate plate = PlateManager.get().getPlate(container, (Integer)oldRow.get("plateId"));
                if (plate != null)
                {
                    int runsInUse = PlateManager.get().getRunCountUsingPlate(container, user, plate);
                    if (runsInUse > 0)
                        throw new QueryUpdateServiceException(String.format("This %s is used by %d runs and its wells cannot be modified.", plate.isTemplate() ? "Plate template" : "Plate", runsInUse));
                }
            }
            return super.updateRow(user, container, row, oldRow, configParameters);
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            // LSID was stripped by super.updateRows() and is needed to insert into the well provisioned table
            String lsid = (String)oldRow.get("lsid");
            if (lsid == null)
                throw new ValidationException("lsid required to update row");

            // update assay.well
            Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, row, oldRow, keys));

            // update provisioned table
            if (_provisionedTable != null)
            {
                keys = new Object[] {lsid};
                ret.putAll(Table.update(user, _provisionedTable, row, _provisionedTable.getColumn("lsid"), keys, null, Level.DEBUG));
            }

            ret.put("lsid", lsid);
            return ret;
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
        {
            Object[] keys = getKeys(row, c);
            Table.delete(getDbTable(), keys);

            // delete the provisioned table row
            if (_provisionedTable != null)
                Table.delete(_provisionedTable, keys);
        }
    }

    protected class WellTableTrigger implements Trigger
    {
        private final HashSet<Integer> mutatedWellRowIds = new HashSet<>();

        private void addWellId(@Nullable Map<String, Object> newRow)
        {
            if (newRow != null && newRow.containsKey("RowId") && newRow.getOrDefault("SampleId", null) != null)
            {
                Integer wellRowId = (Integer) newRow.get("RowId");
                if (wellRowId != null)
                    mutatedWellRowIds.add(wellRowId);
            }
        }

        @Override
        public void complete(
            TableInfo table,
            Container c,
            User user,
            TriggerType event,
            BatchValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (errors.hasErrors())
                return;
            PlateManager.get().validatePrimaryPlateSetUniqueSamples(mutatedWellRowIds, errors);
        }

        @Override
        public void afterInsert(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            addWellId(newRow);
        }

        @Override
        public void afterUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            addWellId(newRow);
        }
    }
}
