package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.Map;

public class QueryWith
{
    final private Map<String, QueryRelation> _withRelations = new LinkedCaseInsensitiveMap<>();
    final private Query _query;
    final private SQLFragment _withSql = new SQLFragment();

    public QueryWith(@NotNull Query query, @NotNull QWith qwith)
    {
        _query = query;
        SqlDialect dialect = query.getSchema().getDbSchema().getSqlDialect();
        qwith.children().forEach(qnode -> {
            if (qnode instanceof QAs)
            {
                QAs qAs = (QAs)qnode;

                // With's QAs is backwards from other QAs's
                QNode expr = qAs.getLastChild();
                if (expr instanceof QQuery || expr instanceof QUnion)
                {
                    QIdentifier alias = (QIdentifier)qAs.getFirstChild();
                    QTable table = new QTable((QExpr)expr);
                    table.setAlias(alias);
                    FieldKey aliasKey = table.getAlias();
                    if (null == aliasKey)
                        query.reportError("Null alias in With.");
                    else
                    {
                        String legalName = getLegalName(dialect, aliasKey.getName());
                        if (null != _withSql.getCommonTableExpressionToken(makeCteKey(legalName)))
                            query.reportError(aliasKey.getName() + " already used as CTE name.");
                        else
                        {
                            String cteToken = _withSql.createCommonTableExpressionToken(makeCteKey(legalName), legalName);
                            if (null != query.lookupWithTable(legalName))
                                query.reportError(aliasKey + " was specified more than once in With.");
                            else
                            {
                                QueryTableWith queryTable = new QueryTableWith(query, query.getSchema(), legalName, cteToken);
                                query.putWithTable(legalName, queryTable);

                                // Build relations after creating QueryTable to handle recursive queries
                                QueryRelation withTermRelation = Query.createQueryRelation(query, expr, false);
                                if (null != withTermRelation && 0 == query.getParseErrors().size())
                                {
                                    _withRelations.put(legalName, withTermRelation);
                                    queryTable.setTableInfo(withTermRelation.getTableInfo());
                                    queryTable.setParsingWith(false);       // Done parsing the query itself
                                }
                                else
                                {
                                    query.removeWithTable(legalName);
                                }
                            }
                        }
                    }
                }
                else
                {
                    // error
                    query.reportError("Expected Query or Union in With.");
                }
            }
            else
            {
                // error
                query.reportError("With must only have QAs children.");
            }
        });
    }

    public SQLFragment getQueryWithSql()
    {
        for (Map.Entry<String, QueryRelation> entry : getWithRelations().entrySet())
        {
            _withSql.setCommonTableExpressionSql(makeCteKey(entry.getKey()), entry.getValue().getSql(), _query.hasRecursiveWith());
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
