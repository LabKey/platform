package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * User: kevink
 * Date: 5/17/13
 *
 * ExtendedTable is used to combine two vertically partitioned tables into a single table.
 * Conceptually, columns from a extendedTable are added to an existing baseTable.
 * The extended table must have a foreign key to the base table.
 * CONSIDER: Perhaps also require the PK on both tables to be the same.
 */
public abstract class ExtendedTable<SchemaType extends UserSchema> extends SimpleUserSchema.SimpleTable<UserSchema>
{
    private final TableInfo _baseTable;
    private final boolean _insertSupported;
    private final boolean _updateSupported;
    private final boolean _deleteSupported;

    private ForeignKey _extendedForeignKey;

    public ExtendedTable(SchemaType userSchema, @NotNull TableInfo extendedTable, @NotNull TableInfo baseTable)
    {
        super(userSchema, extendedTable);
        _baseTable = baseTable;

        if (extendedTable instanceof UpdateableTableInfo && baseTable instanceof UpdateableTableInfo)
        {
            _insertSupported = ((UpdateableTableInfo) baseTable).insertSupported() && ((UpdateableTableInfo) extendedTable).insertSupported();
            _updateSupported = ((UpdateableTableInfo) baseTable).updateSupported() && ((UpdateableTableInfo) extendedTable).updateSupported();
            _deleteSupported = ((UpdateableTableInfo) baseTable).deleteSupported() && ((UpdateableTableInfo) extendedTable).deleteSupported();
        }
        else
        {
            _insertSupported = _updateSupported = _deleteSupported = false;
        }
    }

    protected TableInfo getBaseTable()
    {
        return _baseTable;
    }

    /**
     * The base lookup key is the column from the base table used as the join target.
     * @return
     */
    protected abstract ColumnInfo getBaseLookupKeyColumn();

    /**
     * The merge foreign key is the column from the merged table used to join to the base table.
     * @return
     */
    protected abstract ColumnInfo getExtendedForeignKeyColumn();

    protected ForeignKey getExtendedForeignKey()
    {
        if (_extendedForeignKey == null)
            _extendedForeignKey = createMergeForeignKey();
        return _extendedForeignKey;
    }

    protected abstract ForeignKey createMergeForeignKey();

    protected ColumnInfo addBaseTableColumn(String baseColName, @Nullable String newColName)
    {
        ColumnInfo col = getExtendedForeignKey().createLookupColumn(getExtendedForeignKeyColumn(), baseColName);

        if (newColName != null)
            col.setName(newColName);

        safeAddColumn(col);

        return col;
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return super.hasPermission(user, perm) && getBaseTable().hasPermission(user, perm);
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        // XXX: How to restrict QUS to only ETL updatable tables?
        QueryUpdateService baseQUS = getBaseTable().getUpdateService();
        if (baseQUS instanceof AbstractQueryUpdateService)
        {
            return new ExtendedTableUpdateService(this, this.getRealTable(), (AbstractQueryUpdateService)baseQUS);
        }
        return null;
    }

    //
    // UpdateableTableInfo
    //

    @Override
    public boolean insertSupported()
    {
        return _insertSupported;
    }

    @Override
    public boolean updateSupported()
    {
        return _updateSupported;
    }

    @Override
    public boolean deleteSupported()
    {
        return _deleteSupported;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return getRealTable();
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return null;
    }

    @Nullable
    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Nullable
    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        if (getRealTable() instanceof UpdateableTableInfo && getBaseTable() instanceof UpdateableTableInfo)
        {
            DataIteratorBuilder builder = ((UpdateableTableInfo)getBaseTable()).persistRows(data, context);
            //DataIteratorBuilder builder = new MergedTableDataIteratorBuilder(data, context);
            return ((UpdateableTableInfo)getRealTable()).persistRows(builder, context);
            //return TableInsertDataIterator.create(data, this, null, context);
        }
        return null;
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return StatementUtils.insertStatement(conn, getRealTable(), null, user, false, true);
    }

    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        return StatementUtils.updateStatement(conn, getRealTable(), null, user, false, true);
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    /*
    private class MergedTableDataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        MergedTableDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;

            //TableInfo tableInfo = ExtendedTable.this.getBaseTable();
            final SimpleTranslator it = new SimpleTranslator(input, context);

            return LoggingDataIterator.wrap(it);
        }
    }
    */
}
