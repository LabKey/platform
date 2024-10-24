package org.labkey.assay.plate.query;

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
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
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.UnexpectedException;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.TsvPlateLayoutHandler;
import org.labkey.assay.plate.data.WellTriggerFactory;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.query.ExprColumn.STR_TABLE_ALIAS;

public class WellTable extends SimpleUserSchema.SimpleTable<PlateSchema>
{
    public static final String NAME = "Well";
    public static final String WELL_LOCATION = "WellLocation";

    public enum Column
    {
        Amount,
        Concentration,
        Col,
        Container,
        Dilution,
        Lsid,
        PlateId,
        Position,
        Row,
        RowId,
        SampleID,
        Type,
        Value,
        WellGroup;

        public FieldKey fieldKey()
        {
            return FieldKey.fromParts(name());
        }
    }

    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private static final Set<String> ignoredColumns = new CaseInsensitiveHashSet();
    private final boolean _allowInsertDelete;

    static
    {
        defaultVisibleColumns.add(Column.PlateId.fieldKey());
        defaultVisibleColumns.add(Column.Row.fieldKey());
        defaultVisibleColumns.add(Column.Col.fieldKey());
        defaultVisibleColumns.add(Column.Position.fieldKey());
        defaultVisibleColumns.add(Column.Concentration.fieldKey());
        defaultVisibleColumns.add(Column.Amount.fieldKey());

        // for now don't surface value and dilution, we may choose to drop these fields from the
        // db schema at some point
        ignoredColumns.add(Column.Value.name());
        ignoredColumns.add(Column.Dilution.name());
    }

    public WellTable(PlateSchema schema, @Nullable ContainerFilter cf, boolean allowInsertDelete)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWell(), cf);
        _allowInsertDelete = allowInsertDelete;
        addTriggerFactory(new WellTriggerFactory());
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addWellGroupColumn();
        addPositionColumn();
        addWellMetadataColumns();
        addTypeColumn();
    }

    private void addWellGroupColumn()
    {
        SQLFragment groupSql = new SQLFragment("SELECT WG.Name FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoWellGroupPositions(), "WGP")
                .append(" INNER JOIN ")
                .append(AssayDbSchema.getInstance().getTableInfoWellGroup(), "WG")
                .append(" ON WG.RowId = WGP.WellGroupId ")
                .append(" INNER JOIN ")
                .append(AssayDbSchema.getInstance().getTableInfoPlate(), "P")
                .append(" ON P.RowId = WG.PlateId ")
                .append(" WHERE P.AssayType = ? AND WGP.WellId = " + STR_TABLE_ALIAS + ".RowId")
                .add(TsvPlateLayoutHandler.TYPE);

        // The underlying schema allows for multiple well groups per well, however, as a "well group" column
        // we do not support having multiple values. Here we limit the query to return a single result.
        groupSql = new SQLFragment("(").append(getSqlDialect().limitRows(groupSql, 1)).append(")");

        var column = new ExprColumn(this, Column.WellGroup.fieldKey(), groupSql, JdbcType.VARCHAR);
        column.setLabel("Group");
        column.setUserEditable(true);
        column.setShownInInsertView(true);
        column.setDescription("Identifies the group to which the well belongs.");
        addColumn(column);
    }

    private void addPositionColumn()
    {
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
        var positionCol = new ExprColumn(this, Column.Position.fieldKey(), positionSql, JdbcType.VARCHAR);
        positionCol.setSortFieldKeys(List.of(Column.RowId.fieldKey()));
        positionCol.setUserEditable(false);
        positionCol.setDescription("Indicates the position of the well in the plate.");
        addColumn(positionCol);
    }

    private void addTypeColumn()
    {
        SQLFragment wellTypeSql = new SQLFragment("SELECT DISTINCT WG.TypeName FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoWellGroupPositions(), "WGP")
                .append(" INNER JOIN ")
                .append(AssayDbSchema.getInstance().getTableInfoWellGroup(), "WG")
                .append(" ON WG.RowId = WGP.WellGroupId ")
                .append(" INNER JOIN ")
                .append(AssayDbSchema.getInstance().getTableInfoPlate(), "P")
                .append(" ON P.RowId = WG.PlateId ")
                .append(" WHERE P.AssayType = ? AND WGP.WellId = " + STR_TABLE_ALIAS + ".RowId")
                .add(TsvPlateLayoutHandler.TYPE);

        // The underlying schema allows for multiple well groups per well, however, as a "well type" column
        // we do not support having multiple values. Here we limit the query to return a single result.
        wellTypeSql = new SQLFragment("(").append(getSqlDialect().limitRows(wellTypeSql, 1)).append(")");

        var column = new ExprColumn(this, Column.Type.fieldKey(), wellTypeSql, JdbcType.VARCHAR);
        column.setFk(new QueryForeignKey(getUserSchema().getTable(WellGroupTypeTable.NAME), null, null));
        column.setUserEditable(true);
        column.setShownInInsertView(true);
        column.setDescription("Specifies the type of well.");
        addColumn(column);
    }

    /**
     * Join the well metadata fields into the well table as sibling fields to the columns on the well table.
     */
    private void addWellMetadataColumns()
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
                if (dp != null)
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    if (pd != null)
                    {
                        defaultsSupplier = PropertyColumn.copyAttributes(getUserSchema().getUser(), wrapped, dp, getContainer(), lsidFieldKey, getContainerFilter(), defaultsSupplier);
                        wrapped.setFieldKey(FieldKey.fromParts(dp.getName()));
                    }
                }

                addColumn(wrapped);
            }
        }
    }

    @Override
    protected boolean acceptColumn(ColumnInfo col)
    {
        return super.acceptColumn(col) && !ignoredColumns.contains(col.getName());
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo col)
    {
        var columnInfo = super.wrapColumn(col);

        // workaround for sample lookup not resolving correctly
        if (columnInfo.getName().equalsIgnoreCase(Column.SampleID.name()))
        {
            columnInfo.setFk(QueryForeignKey.from(getUserSchema(), getContainerFilter())
                    .schema(ExpSchema.SCHEMA_NAME, getContainer())
                    .to(ExpSchema.TableType.Materials.name(), ExpMaterialTable.Column.RowId.name(), ExpMaterialTable.Column.Name.name()));
        }
        return columnInfo;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return Collections.unmodifiableList(defaultVisibleColumns);
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return getFromSQL(alias, null);
    }

    @Override
    protected SQLFragment getFromSQLExpanded(String alias, Set<FieldKey> cols)
    {
        // join the base assay.well table to the provisioned table
        checkReadBeforeExecute();

        Set<String> baseColumns = new CaseInsensitiveHashSet(_rootTable.getColumnNameSet());

        // all columns from provisioned table except lsid
        TableInfo wellProperties = PlateManager.get().getPlateMetadataTable(getContainer(), _userSchema.getUser());
        Set<String> provisionedColumns = Collections.emptySet();
        if (wellProperties != null)
        {
            provisionedColumns = new CaseInsensitiveHashSet(wellProperties.getColumnNameSet());
            provisionedColumns.remove("lsid");
        }

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

        sql.append(" FROM ").append(_rootTable, "d");

        if (!provisionedColumns.isEmpty())
        {
            sql.append(" INNER JOIN ").append(wellProperties, "p").append(" ON d.lsid = p.lsid");
        }
        String subAlias = alias + "_wp_sub";
        sql.append(") ").append(subAlias);
        sql.append("\n");

        // add the WHERE clause
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), subAlias, columnMap);
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return sql;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo provisionedTable = null;
        final Container container = getContainer();
        final User user = getUserSchema().getUser();

        // Ensure the plate metadata domain exists for Biologics folders before creating a plate.
        Domain domain = PlateManager.get().getPlateMetadataDomain(container, user);
        if (domain == null && AssayPlateMetadataService.isBiologicsFolder(container))
        {
            try
            {
                domain = PlateManager.get().ensurePlateMetadataDomain(container, user);
            }
            catch (ValidationException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        if (domain != null)
            provisionedTable = StorageProvisioner.createTableInfo(domain);

        return new WellUpdateService(this, AssayDbSchema.getInstance().getTableInfoWell(), provisionedTable);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!_allowInsertDelete && (InsertPermission.class.equals(perm) || DeletePermission.class.equals(perm)))
            return false;
        return super.hasPermission(user, perm);
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
            if (lsidRemover.getColumnNameMap().containsKey(Column.Lsid.name()))
            {
                // remove any furnished lsid since we will be computing one
                lsidRemover.removeColumn(lsidRemover.getColumnNameMap().get(Column.Lsid.name()));
            }

            SimpleTranslator lsidGenerator = new SimpleTranslator(lsidRemover, context);
            lsidGenerator.setDebugName("lsidGenerator");
            lsidGenerator.selectAll();
            final Map<String, Integer> nameMap = lsidGenerator.getColumnNameMap();

            // consider enforcing this in the schema
            if (!nameMap.containsKey(Column.Row.name()) || !nameMap.containsKey(Column.Col.name()))
            {
                context.getErrors().addRowError(new ValidationException("Row and Col are required fields"));
                return data;
            }

            // generate a value for the lsid
            lsidGenerator.addColumn(wellTable.getColumn(Column.Lsid.name()),
                    (Supplier) () -> {
                        Object row = lsidGenerator.get(nameMap.get("row"));
                        Object col = lsidGenerator.get(nameMap.get("col"));

                        Lsid lsid = PlateManager.get().getLsid(Well.class, container);
                        return String.format("%s-well-%s-%s", lsid.toString(), row, col);
                    });

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(wellTable, lsidGenerator, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, wellTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet(Column.RowId.name(), Column.Lsid.name()));
            if (_provisionedTable != null)
            {
                dib = new TableInsertDataIteratorBuilder(dib, _provisionedTable, container)
                        .setKeyColumns(new CaseInsensitiveHashSet(Column.Lsid.name()));
            }
            dib = LoggingDataIterator.wrap(dib);
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(wellTable, dib, context.getInsertOption(), user, container);

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
        protected Map<String, Object> updateRow(
            User user,
            Container container,
            Map<String, Object> row,
            @NotNull Map<String, Object> oldRow,
            @Nullable Map<Enum, Object> configParameters
        ) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            // enforce no updates if the plate has been imported in an assay run
            if (oldRow.containsKey(Column.PlateId.name()))
            {
                Plate plate = PlateManager.get().getPlate(container, (Integer) oldRow.get(Column.PlateId.name()));
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
        protected Map<String, Object> _update(
            User user,
            Container c,
            Map<String, Object> row,
            Map<String, Object> oldRow,
            Object[] keys
        ) throws SQLException, ValidationException
        {
            // LSID was stripped by super.updateRows() and is needed to insert into the well provisioned table
            String lsid = (String) oldRow.get(Column.Lsid.name());
            if (lsid == null)
                throw new ValidationException("lsid required to update row");

            // update assay.well
            Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, row, oldRow, keys));

            // update provisioned table
            if (_provisionedTable != null)
            {
                keys = new Object[] {lsid};
                ret.putAll(Table.update(user, _provisionedTable, row, _provisionedTable.getColumn(Column.Lsid.name()), keys, null, Level.DEBUG));
            }

            ret.put(Column.Lsid.name(), lsid);
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
}
