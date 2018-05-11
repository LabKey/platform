package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.List;
import java.util.Map;

public class QueryWith
{
    final private Map<String, QueryRelation> _withRelations = new LinkedCaseInsensitiveMap<>();
    final private Query _query;
    final private SQLFragment _withSql = new SQLFragment();

    public QueryWith(@NotNull Query query, @NotNull QWith qwith)
    {
        _query = query;
        List<QueryException> errors = _query.getParseErrors();
        SqlDialect dialect = query.getSchema().getDbSchema().getSqlDialect();
        if (!dialect.isLabKeyWithSupported())
        {
            Query.parseError(errors, "WITH not supported for " + dialect.getProductName(), qwith);
        }
        else
        {
            qwith.children().forEach(qnode -> {
                if (qnode instanceof QAs)
                {
                    QAs qAs = (QAs) qnode;

                    // With's QAs is backwards from other QAs's
                    QNode expr = qAs.getLastChild();
                    if (expr instanceof QQuery || expr instanceof QUnion)
                    {
                        QIdentifier alias = (QIdentifier) qAs.getFirstChild();
                        QTable table = new QTable((QExpr) expr);
                        table.setAlias(alias);
                        FieldKey aliasKey = table.getAlias();
                        if (null == aliasKey)
                            Query.parseError(errors, "Null alias in With.", expr);
                        else
                        {
                            String legalName = getLegalName(dialect, aliasKey.getName());
                            if (null != _withSql.getCommonTableExpressionToken(makeCteKey(legalName)))
                                Query.parseError(errors, aliasKey.getName() + " already used as CTE name.", expr);
                            else
                            {
                                String cteToken = _withSql.createCommonTableExpressionToken(makeCteKey(legalName), legalName);
                                if (null != _query.lookupWithTable(legalName))
                                    Query.parseError(errors, aliasKey + " was specified more than once in With.", expr);
                                else
                                {
                                    QueryTableWith queryTable = new QueryTableWith(_query, _query.getSchema(), legalName, cteToken);
                                    _query.putWithTable(legalName, queryTable);

                                    // Build relations after creating QueryTable to handle recursive queries
                                    QueryRelation withTermRelation = Query.createQueryRelation(_query, expr, false);
                                    if (null != withTermRelation && 0 == _query.getParseErrors().size())
                                    {
                                        _withRelations.put(legalName, withTermRelation);
                                        TableInfo tableInfo = withTermRelation.getTableInfo();
                                        if (null != tableInfo)           // Getting table can reveal parse errors
                                            queryTable.setTableInfo(tableInfo);
                                        queryTable.setParsingWith(false);       // Done parsing the query itself
                                    }
                                    else
                                    {
                                        _query.removeWithTable(legalName);
                                    }
                                }
                            }
                        }
                    }
                    else
                    {
                        // error
                        Query.parseError(errors, "Expected Query or Union in With.", expr);
                    }
                }
                else
                {
                    // error
                    Query.parseError(errors, "With must only have QAs children.", qnode);
                }
            });
        }
    }

    public SQLFragment getQueryWithSql()
    {
        boolean dialectNeedsRecursive = _query.getSchema().getDbSchema().getSqlDialect().isWithRecursiveKeywordRequired();
        for (Map.Entry<String, QueryRelation> entry : getWithRelations().entrySet())
        {
            _withSql.setCommonTableExpressionSql(makeCteKey(entry.getKey()), entry.getValue().getSql(),
                    (dialectNeedsRecursive && _query.hasRecursiveWith()));
        }
        return _withSql;
    }

    private Map<String, QueryRelation> getWithRelations()
    {
        return _withRelations;
    }

    private String makeCteKey(String legalName)
    {
        return "_with003388_%$&_" + legalName;
    }

    public static String getLegalName(SqlDialect dialect, String name)
    {
        return dialect.makeLegalIdentifier(name);
    }

    public class QueryTableWith extends QueryTable
    {
        private boolean _seenRecursiveReference = false;
        private boolean _parsingWith = true;
        private boolean _settingContainerFilter = false;    // prevent endless recursion
        final private String _cteToken;

        public QueryTableWith(Query query, QuerySchema schema, String alias, String cteToken)
        {
            super(query, schema, alias);
            _cteToken = cteToken;
        }

        @Override
        public SQLFragment getFromSql()
        {
            // Reference to With
            _generateSelectSQL = true;
            return new SQLFragment("(SELECT * FROM ").append(_cteToken).append(") ").append(getAlias());
        }

        @Override
        public void setContainerFilter(ContainerFilter containerFilter)
        {
            if (!_settingContainerFilter)
            {
                _settingContainerFilter = true;
                super.setContainerFilter(containerFilter);
                _settingContainerFilter = false;
            }
        }

        public boolean isSeenRecursiveReference()
        {
            return _seenRecursiveReference;
        }

        public void setSeenRecursiveReference(boolean seenRecursiveReference)
        {
            _seenRecursiveReference = seenRecursiveReference;
        }

        public boolean isParsingWith()
        {
            return _parsingWith;
        }

        public void setParsingWith(boolean parsingWith)
        {
            _parsingWith = parsingWith;
        }
    }
}
