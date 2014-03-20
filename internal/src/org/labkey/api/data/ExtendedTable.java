/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.ExtendedTableUpdateService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
     * @return The column from the base table that is the target of the extended foreign key lookup.
     */
    protected abstract ColumnInfo getBaseLookupKeyColumn();

    /**
     * The extended foreign key column is the column from the extended table used to join to the base table
     * using the {@link #getExtendedForeignKey()} foreign key.
     */
    protected abstract ColumnInfo getExtendedForeignKeyColumn();

    protected ForeignKey getExtendedForeignKey()
    {
        if (_extendedForeignKey == null)
            _extendedForeignKey = createExtendedForeignKey();
        return _extendedForeignKey;
    }

    protected abstract ForeignKey createExtendedForeignKey();

    private List<ColumnInfo> _baseCols = new ArrayList<>();

    /**
     * Creates a lookup column by traversing the extended foreign key and adding it to the current table.
     * @param baseColName The column name from the base table.
     * @param newColName The name used to add the lookup column to this table.
     * @return The newly created lookup column.
     */
    protected ColumnInfo addBaseTableColumn(String baseColName, @Nullable String newColName)
    {
        ColumnInfo col = getExtendedForeignKey().createLookupColumn(getExtendedForeignKeyColumn(), baseColName);
        _baseCols.add(col);

        if (newColName != null)
            col.setName(newColName);
        safeAddColumn(col);
        return col;

        // XXX: I think we should be wrapping the columns onto this table instead of adding them directly
        //newColName = Objects.toString(newColName, baseColName);
        //ColumnInfo wrapped = wrapColumn(newColName, col);
        //safeAddColumn(wrapped);
        //return wrapped;
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return super.hasPermission(user, perm) && getBaseTable().hasPermission(user, perm);
    }

    /*
    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        //SQLFragment frag = super.getFromSQL(alias);

        // SELECT
        String sep = "";
        SQLFragment ret = new SQLFragment("(SELECT ");
        for (ColumnInfo baseCol : _baseCols)
        {
            //ret.append(sep).append("base.").append(baseCol.getValueSql());
            ret.append(sep).append(baseCol.getValueSql("base"));
            sep = ", ";
        }
        ret.append(", ext.*");

        // FROM
        ret.append(" FROM ").append(_baseTable, "base");
        ret.append(", ").append(_rootTable, "ext");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        ret.append("\n").append(filterFrag).append(") ").append(alias);

        return ret;
    }

    @Override
    protected TableInfo getFromTable()
    {
        return new VirtualTable<UserSchema>(getSchema(), getUserSchema())
        {
            @NotNull
            @Override
            public SQLFragment getFromSQL()
            {
                SQLFragment sql = new SQLFragment();
                sql.append("SELECT * FROM ");
                sql.append(getRealTable(), "real");
                sql.append(", ");
                sql.append(_baseTable, "base");
                sql.append(" WHERE ");
                sql.append("real.").append(getExtendedForeignKeyColumn().getAlias());
                sql.append(" = ");
                sql.append("base.").append(getBaseLookupKeyColumn().getAlias());
                //sql.append(getFromSQL());
                return sql;
            }
        };
    }
    */

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
            return ((UpdateableTableInfo)getRealTable()).persistRows(builder, context);
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

}
