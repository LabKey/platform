package org.labkey.query.sql;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.UserSchema;
import org.labkey.query.QueryServiceImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.query.ExprColumn.STR_TABLE_ALIAS;

/**
 * {@link ColumnInfo} backed by a LabKey SQL fragment
 */
public class LabKeyExprColumn extends BaseColumnInfo
{
    private static final Logger LOG = LogManager.getLogger(LabKeyExprColumn.class);

    // Query-layer LabKey SQL fragment
    private String _labKeySql;

    // Parsed expression
    private QExpr _expr;
    private Query _query;
    private QueryRelation _rootRelation;
    private final List<QueryRelation> _subqueries = new ArrayList<>();
    private boolean _resolved;
    private boolean _decalareCalled;

    private final ColumnInfo[] _dependentColumns;

    public LabKeyExprColumn(TableInfo parent, FieldKey key, String labKeySql, JdbcType type, ColumnInfo ... dependentColumns)
    {
        super(key, parent);
        setJdbcType(type);
        _labKeySql = labKeySql;
        if (dependentColumns != null)
        {
            for (ColumnInfo dependentColumn : dependentColumns)
            {
                if (dependentColumn == null)
                {
                    throw new NullPointerException("Dependent columns may not be null");
                }
            }
        }
        // Since these are typically calculated columns, it doesn't make sense to show them in update or insert views
        setShownInUpdateView(false);
        setShownInInsertView(false);
        setUserEditable(false);
        setCalculated(true);
        // Unless otherwise configured, guess that it might be nullable
        setNullable(true);
        _dependentColumns = dependentColumns;
    }

    private QExpr parse(String tableAlias)
    {
        if (_expr == null)
        {
            TableInfo parentTable = getParentTable();
            UserSchema schema = parentTable.getUserSchema();

            _query = new Query(schema);
            _query.setDebugName(schema.getName() + "." + parentTable.getName() + "." + getName());

            List<QueryParseException> errors = new ArrayList<>();
            SqlParser parser = new SqlParser(getSqlDialect(), null);
            String sql = StringUtils.replace(_labKeySql, STR_TABLE_ALIAS, tableAlias);
            QExpr expr = parser.parseExpr(sql, errors);
            // better error handling
            if (!errors.isEmpty())
            {
                LOG.warn("Failed to parse");
                // TODO: for non-string types, use NULL
                expr = parser.parseExpr("'ERROR'", new ArrayList<>());
            }

            _query._parameters = parser.getParameters();

            _rootRelation = Query.createQueryRelation(_query, expr, false, true);

            if (_dependentColumns == null)
            {
                // TODO: get dependent columns from parsed expr
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
            QueryRelation sub = ((QQuery)expr).getQuerySelect();
            if (sub == null)
            {
                sub = Query.createQueryRelation(_query, (QQuery)expr, false);
                // ?
//                sub._parent = _rootRelation;
                ((QQuery)expr)._select = sub;
                _subqueries.add(sub);
            }
            sub.declareFields();

            // XXX: get select fields from sub?
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
    private QExpr resolveFields(String tableAliasName, QExpr expr, Map<FieldKey, ? extends ColumnInfo> columnMap)
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
                throw new UnsupportedOperationException("column not found: " + key);
            return new QueryServiceImpl.QColumnInfo(tableAliasName, c);
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
                ret.appendChild(resolveFields(tableAliasName, (QExpr) child, columnMap));
        }
        return ret;
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        QExpr parsed = parse(tableAliasName);
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

        QExpr bound = resolveFields(tableAliasName, parsed, columnMap);
        return bound.getSqlFragment(getSqlDialect(), null);
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        if (_dependentColumns != null)
        {
            for (ColumnInfo col : _dependentColumns)
            {
                col.declareJoins(parentAlias, map);
            }
        }
    }
}
