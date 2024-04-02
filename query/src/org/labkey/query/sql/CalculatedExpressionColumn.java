package org.labkey.query.sql;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumnFactory;
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
import org.labkey.api.util.logging.LogHelper;
import org.labkey.data.xml.TableType;
import org.labkey.query.QueryServiceImpl;

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
    private static final Logger LOG = LogHelper.getLogger(CalculatedExpressionColumn.class, "Calculated Expression Column");

    // Query-layer LabKey SQL fragment
    private final String _labKeySql;

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

//    private final ColumnInfo[] _dependentColumns;

    public CalculatedExpressionColumn(TableInfo parent, FieldKey key, String labKeySql)
    {
        super(key, parent);
        _labKeySql = labKeySql;
        // Since these are typically calculated columns, it doesn't make sense to show them in update or insert views
        super.setShownInUpdateView(false);
        super.setShownInInsertView(false);
        super.setUserEditable(false);
        super.setCalculated(true);
        // Unless otherwise configured, guess that it might be nullable
        setNullable(true);

        // TODO we can't (yet) call getBoundExpression() while our parent table is still being constructed.
        setJdbcType(null);
    }

    @Override
    public void setCalculated(boolean calculated)
    {
        // can't touch this e.g. copyAttributesFrom()
    }

    @Override
    public void setUserEditable(boolean editable)
    {
        // can't touch this e.g. copyAttributesFrom()
    }

    @Override
    public @NotNull JdbcType getJdbcType()
    {
        if (null == expressionJdbcType)
        {
            var bound = getBoundExpression();
            expressionJdbcType = bound.getJdbcType();
        }
        return expressionJdbcType;
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

    // copied from QueryServiceImpl.WhereClause
    private void collectKeys(QExpr expr, Set<FieldKey> set)
    {
//        if (expr instanceof QQuery || expr instanceof QUnion || expr instanceof QRowStar || expr instanceof QIfDefined)
//            return;
        if (expr instanceof QQuery)
        {
//            QueryRelation sub = ((QQuery)expr).getQuerySelect();
//            if (sub == null)
//            {
//                sub = Query.createQueryRelation(_query, (QQuery)expr, false);
//                // ?
////                sub._parent = _rootRelation;
//                ((QQuery)expr)._select = sub;
//                _subqueries.add(sub);
//            }
//            sub.declareFields();
//
//            // XXX: get select fields from sub?
            // TODO
            return;
        }
        if (expr instanceof QUnion)
        {
            // TODO
        }
        if (expr instanceof QRowStar)
        {
            // TODO
        }
        if (expr instanceof QIfDefined)
        {
            // TODO
//            ((QIfDefined)expr).setQuerySelect(this);
            return;
        }

        FieldKey key = expr.getFieldKey();
        if (key != null)
            set.add(key);

        QExpr methodName = null;
        if (expr instanceof QMethodCall)
        {
            methodName = (QExpr) expr.childList().get(0);
            if (null == methodName.getFieldKey())
                methodName = null;
        }

        if (!(expr instanceof QDot))
        {
            for (QNode child : expr.children())
            {
                // skip identifier that is actually a method
                if (child != methodName)
                    collectKeys((QExpr) child, set);
            }
        }
    }

    /*
     * Return the result of replacing field names in the expression with QField objects.
     */
    // copied from QueryServiceImpl.WhereClause
    // copied from QuerySelect.resolveFields
    private QExpr resolveFields(QExpr expr, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
//        if (expr instanceof QQuery || expr instanceof QUnion || expr instanceof QRowStar || expr instanceof QIfDefined)
//        {
//            throw new QueryParseException("unsupported expression: " + expr.getTokenText());
//        }
        if (expr instanceof QQuery || expr instanceof QUnion)
        {
            QueryRelation subquery;
            if (expr instanceof QQuery)
                subquery = ((QQuery)expr)._select;
            else
                subquery = ((QUnion)expr)._union;
            if (null == subquery)
            {
                throw new QueryParseException("Unexpected error: sub query not resolved", null, 0, 0);
            }
            else
            {
                subquery.resolveFields();
            }
            return expr;
        }
        if (expr instanceof QRowStar)
        {
            return expr;
        }
        if (expr instanceof QIfDefined && !((QIfDefined)expr).isDefined)
        {
            return new QNull();
        }

        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            ColumnInfo c = columnMap.get(key);
            if (null == c)
                throw new QueryParseException("'" + key + "' not found.", null, 0, 0);
            if (c.getPHI() != null && !c.getPHI().isLevelAllowed(PHI.NotPHI))
                throw new QueryParseException("'" + key + "' has PHI, it cannot be used in a calculated expression." + key, null, 0, 0);
            return new _BoundColumn(key);
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
                ret.appendChild(new QField(null, ((QExpr) child).getFieldKey().getName(), child));
            else
                ret.appendChild(resolveFields((QExpr) child, columnMap));
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
        SQLFragment ret;
        ret = bound.getSqlFragment(getSqlDialect(), new _Query(tableAliasName));
        return ret;
    }


    public void validate(Map<FieldKey, ColumnInfo> columnMap) throws QueryParseException
    {
        QExpr parsed = parse();
        QExpr expr = resolveFields(parsed, columnMap);
    }


    private QExpr getBoundExpression()
    {
        if (null == _boundExpr)
        {
            QExpr parsed = parse();
            Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getParentTable(), getParentTable().getColumns());
            columnMap.remove(getFieldKey());
            _boundExpr = resolveFields(parsed, columnMap);
        }
        return _boundExpr;
    }


    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
//        if (_dependentColumns != null)
//        {
//            for (ColumnInfo col : _dependentColumns)
//            {
//                col.declareJoins(parentAlias, map);
//            }
//        }
    }

    @Override
    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return super.getDisplayColumnFactory();
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

        public BaseColumnInfo addColumn(String name, JdbcType type, String alias)
        {
            BaseColumnInfo c = new BaseColumnInfo(name, null, type);
            c.setMetaDataName(alias);
            addColumn(c);
            return c;
        }
    }
}
