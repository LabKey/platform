package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaColumnMetaData;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.UserSchema;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ColumnInfo} backed by a LabKey SQL fragment
 */
public class CalculatedExpressionColumn extends BaseColumnInfo
{
    // Query-layer LabKey SQL fragment
    private final String _labKeySql;
    private final ColumnType _xmlColumn;
    private HashSet<FieldKey> _allFieldKeys;
    private QueryParseException _resolveException;

    // Parsed expression -- READ ONLY
    private QExpr _parsedExpr;

    // Bound expression -- also READ ONLY, in this usage
    // The bound expression is almost identical to the parsed expression.
    // The difference is that it has been validated and columns have been resolved to FieldKey objects.
    // We don't resolve to ColumnInfo because we would need to do that as the very last step of TableInfo construction
    // and I don't want to add another step.  Consider TableCustomizers, ColumnInfoTransformers and the like.  Most of these
    // wouldn't materially affect the result of the expression, but they do affect metadata (DisplayColumnFactory, etc.)
    private QExpr _boundExpr;

    private JdbcType expressionJdbcType = null;


    public CalculatedExpressionColumn(TableInfo parent, FieldKey key, String labKeySql, ColumnType columnType)
    {
        super(key, parent);
        _labKeySql = labKeySql;
        _xmlColumn = columnType;
    }


    @Override
    public @Nullable String getWrappedColumnName()
    {
        var bound = getBoundExpression();
        if (bound instanceof _BoundColumn)
        {
            FieldKey key = bound.getFieldKey();
            if (null != key && null == key.getParent())
                return key.getName();
        }
        return null;
    }


    public void validate(Map<FieldKey, ColumnInfo> columnMap, @Nullable Set<FieldKey> referencedKeys) throws QueryParseException
    {
        try
        {
            QExpr parsed = parse();
            resolveFields(parsed, columnMap);
        }
        finally
        {
            if (null != referencedKeys && null != _allFieldKeys)
                referencedKeys.addAll(_allFieldKeys);
        }
    }


    public void computeMetaData(Map<FieldKey,ColumnInfo> columns)
    {
        if (null == columns)
            columns = Table.createColumnMap(getParentTable(), null);

        // set properties based on the expression
        QExpr bound = getBoundExpression(columns);
        ColumnInfo from = null;
        if (bound instanceof _BoundColumn)
            from = columns.get(bound.getFieldKey());
        else
            from = bound.createColumnInfo(getParentTable(), getColumnName(), null);
        FieldKey me = getFieldKey();
        if (null != from)
            this.copyAttributesFrom(from);
        setLabel(null);
        setFieldKey(me);

        // set CalculatedExpressionColumn properties (over-writable)
        super.setShownInUpdateView(false);
        super.setShownInInsertView(false);

        if (null != _xmlColumn)
            this.loadFromXml(_xmlColumn, true);

        // set CalculatedExpressionColumn properties (not over-writable)
        super.setUserEditable(false);
        super.setCalculated(true);
    }


    /**
     * TODO maybe have TableInfo call computeMetadata() in fireAfterConstruct()?
     *<br>
     * We don't have a phase where we the TableInfo notifies it's columns, that it is "done".  But since this object
     * is shared, I'd prefer to calculate the type during construction and not on demand.
     *<br>
     * Nothing breaks if we don't do this here, as we will still calculate the type getJdbcType() if we need to.
     *
     * @param b set locked state
     */
    @Override
    public void setLocked(boolean b)
    {
        computeMetaData(null);
        super.setLocked(b);
    }


    private QExpr parse()
    {
        if (_parsedExpr == null)
        {
            TableInfo parentTable = getParentTable();
            UserSchema schema = parentTable.getUserSchema();
            List<QueryParseException> errors = new ArrayList<>();

            SqlParser parser = new SqlParser(getSqlDialect(), null==schema ? null : schema.getContainer());
            QExpr expr = parser.parseExpr(_labKeySql, errors);
            if (!errors.isEmpty())
                throw errors.get(0);
            _parsedExpr = expr;
        }

        return _parsedExpr;
    }


    /*
     * Return the result of replacing field names in the expression with QField objects.
     */
    private QExpr resolveFields(QExpr expr, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        /* NOTE this does not throw immediate on encountering an error, as we want to collect all the referenced fields
         * this is used for ParseCalculatedColumnAction.
         */
        _allFieldKeys = new HashSet<>();
        _resolveException = null;
        var ret = _resolveFields(expr, columnMap);
        if (null != _resolveException)
            throw _resolveException;
        return ret;
    }

    private void setResolveException(String msg)
    {
        if (null == _resolveException)
            _resolveException = new QueryParseException(msg, null, 0, 0);
    }

    private QExpr _resolveFields(QExpr expr, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        if (expr instanceof QQuery || expr instanceof QUnion)
        {
            setResolveException("SELECT and UNION are not allowed in calculated columns.");
            return null;
        }
        if (expr instanceof QRowStar)
        {
            setResolveException("SELECT and UNION are not allowed in calculated columns.");
            return null;
        }

        if (expr instanceof QIfDefined && !((QIfDefined)expr).isDefined)
            return new QNull();

        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            _allFieldKeys.add(key);
            if (null != key.getParent())
            {
                setResolveException("Lookup is not allowed '" + key.toSQLString() + "'.");
                return null;
            }
            if (key.equals(this.getFieldKey()))
            {
                setResolveException("Calculated expression can not refer to itself.");
                return null;
            }
            ColumnInfo c = columnMap.get(key);
            if (null == c)
            {
                setResolveException(key.toSQLString() + " not found.");
                return null;
            }
            if (c.getPHI() != null && !c.getPHI().isLevelAllowed(PHI.NotPHI))
            {
                setResolveException(key.toSQLString() + " has PHI, it cannot be used in a calculated expression.");
                return null;
            }
            return new _BoundColumn(key);
        }

        if (expr instanceof QAggregate)
        {
            setResolveException("SELECT and UNION are not allowed in calculated columns.");
            return null;
        }

        QExpr methodName = null;
        if (expr instanceof QMethodCall)
        {
            methodName = (QExpr) expr.childList().get(0);
            if (null == methodName.getFieldKey())
                methodName = null;
        }

        QExpr ret = (QExpr) expr.clone();
        for (QNode child : expr.children())
        {
            if (child == methodName)
            {
                ret.appendChild(new QField(null, ((QExpr) child).getFieldKey().getName(), child));
            }
            else
            {
                var resolved = _resolveFields((QExpr) child, columnMap);
                assert resolved != null || _resolveException != null;
                ret.appendChild(null == resolved ? child : resolved);
            }
        }
        return ret;
    }

    private static class _Query extends Query
    {
        private final String _tableAlias;

        public _Query(String tableAlias)
        {
            super(null);
            _tableAlias = tableAlias;
        }

        public String getTableALias()
        {
            return _tableAlias;
        }
    }

    private class _BoundColumn extends QInternalExpr
    {
        private final FieldKey _fieldKey;

        public _BoundColumn(FieldKey fieldKey)
        {
            super();
            _fieldKey = fieldKey;
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _fieldKey;
        }

        @Override
        public @NotNull JdbcType getJdbcType()
        {
            ColumnInfo column = getParentTable().getColumn(_fieldKey);
            return null == column ? JdbcType.OTHER : column.getJdbcType();
        }

        @Override
        public void appendSql(SqlBuilder builder, Query query)
        {
            ColumnInfo column = getParentTable().getColumn(_fieldKey);
            if (null == column)
                throw new QueryException("Column not found: " + _fieldKey);     // should already be validated.
            builder.append(column.getValueSql(((_Query)query).getTableALias()));
        }

        @Override
        public boolean isConstant()
        {
            return false;
        }
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        QExpr bound = getBoundExpression();
        SqlBuilder sql = new SqlBuilder(getSqlDialect());
        sql.append("(");
        bound.appendSql(sql, new _Query(tableAliasName));
        sql.append(")");
        return sql;
    }


    private QExpr getBoundExpression()
    {
        if (null != _boundExpr)
            return _boundExpr;
        return getBoundExpression(Table.createColumnMap(getParentTable(), null));
    }


    private QExpr getBoundExpression(Map<FieldKey, ColumnInfo> columnMap)
    {
        if (null == _boundExpr)
        {
            QExpr parsed = parse();
            columnMap.remove(getFieldKey());
            _boundExpr = resolveFields(parsed, columnMap);
        }
        return _boundExpr;
    }


    /*
     * TESTS
     */

    public static class _SchemaTableInfo extends SchemaTableInfo
    {
        final private List<MutableColumnInfo> colsToAdd;
        final private TableType xmlToApply;

        public _SchemaTableInfo(DbSchema schema, DatabaseTableType tableType, String tableName,
            List<MutableColumnInfo> cols, TableType xmlTable)
        {
            super(schema, tableType, tableName);
            colsToAdd = cols;
            colsToAdd.forEach(c -> c.setParentTable(this));
            xmlToApply = xmlTable;
        }

        @Override
        protected SchemaColumnMetaData createSchemaColumnMetaData() throws SQLException
        {
            return new SchemaColumnMetaData(this, colsToAdd, xmlToApply);
        }
    }
}
