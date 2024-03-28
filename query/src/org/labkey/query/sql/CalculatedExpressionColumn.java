package org.labkey.query.sql;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.query.QueryServiceImpl;

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

    // Parsed expression
    private QExpr _expr;

    private JdbcType expressionJdbcType = null;

//    private final ColumnInfo[] _dependentColumns;

    public CalculatedExpressionColumn(TableInfo parent, FieldKey key, String labKeySql)
    {
        super(key, parent);
        _labKeySql = labKeySql;
        // Since these are typically calculated columns, it doesn't make sense to show them in update or insert views
        setShownInUpdateView(false);
        setShownInInsertView(false);
        setUserEditable(false);
        setCalculated(true);
        // Unless otherwise configured, guess that it might be nullable
        setNullable(true);

        // TODO we can't (yet) call getBoundExpression() while our parent table is still being constructed.
        setJdbcType(null);
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
        if (_expr == null)
        {
            TableInfo parentTable = getParentTable();
            UserSchema schema = parentTable.getUserSchema();
            List<QueryParseException> errors = new ArrayList<>();

            SqlParser parser = new SqlParser(getSqlDialect(), null==schema ? null : schema.getContainer());
            QExpr expr = parser.parseExpr(_labKeySql, errors);

            // TODO better error handling
            if (!errors.isEmpty())
            {
                LOG.warn("Failed to parse");
                // TODO: for non-string types, use NULL
                expr = parser.parseExpr("'ERROR'", new ArrayList<>());
            }
            _expr = expr;
        }

        return _expr;
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
//            throw new UnsupportedOperationException("unsupported expression: " + expr.getTokenText());
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
                throw new UnsupportedOperationException("Unexpected error: sub query not resolved");
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
                throw new QueryParseException("column not found: " + key, null, 0, 0);
            if (c.getPHI() != null && !c.getPHI().isLevelAllowed(PHI.NotPHI))
                throw new QueryParseException("column is PHI: " + key, null, 0, 0);
            return new _QColumnInfo(c);
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

    private static class _QColumnInfo extends QueryServiceImpl.QColumnInfo
    {
        private final ColumnInfo _column;

        public _QColumnInfo(ColumnInfo column)
        {
            super(column);
            _column = column;
        }

        @Override
        public void appendSql(SqlBuilder builder, Query query)
        {
            builder.append(_column.getValueSql(((_Query)query).getTableALias()));
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


    private QExpr getBoundExpression()
    {
        QExpr parsed = parse();
        Set<FieldKey> fieldKeys = new HashSet<>();
        collectKeys(parsed, fieldKeys);

        // Remove parent from FieldKey if it matches tableAliasName
//        Set<FieldKey> reparentedFieldKey = fieldKeys.stream()
//                .map(fieldKey -> {
//                    if (fieldKey.getRootName().equals(tableAliasName))
//                        return fieldKey.removeParent(tableAliasName);
//                    return fieldKey;
//                }).collect(Collectors.toSet());

//        Map<FieldKey, ColumnInfo> columnMap = QueryService.get().getColumns(getParentTable(), reparentedFieldKey, getParentTable().getColumns());

        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getParentTable(), getParentTable().getColumns());
        columnMap.remove(getFieldKey());

        // NOTE: Create reparentedMap when requiring the <code>STR_TABLE_ALIAS</code> table name
        // add tableAliasName as the parent FieldKey
//        Map<FieldKey, ColumnInfo> reparentedMap = new HashMap<>();
//        FieldKey tableParent = FieldKey.fromParts(tableAliasName);
//        for (Map.Entry<FieldKey, ColumnInfo> entry : columnMap.entrySet())
//        {
//            reparentedMap.put(FieldKey.fromParts(tableParent, entry.getKey()), entry.getValue());
//        }
//
//        QExpr bound = resolveFields(tableAliasName, parsed, reparentedMap);

        QExpr bound = resolveFields(parsed, columnMap);
        return bound;
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
}
