/*
 * Copyright (c) 2018 LabKey Corporation
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
    final private Map<String, WithInfo> _withRelations = new LinkedCaseInsensitiveMap<>();
    final private Query _query;

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
                        QTable table = new QTable((QExpr) expr, null);
                        table.setAlias(alias);
                        FieldKey aliasKey = table.getAlias();
                        if (null == aliasKey)
                            Query.parseError(errors, "Null alias in With.", expr);
                        else
                        {
                            String legalName = getLegalName(dialect, aliasKey.getName());
                            String cteKey = makeCteKey(legalName);

                            if (null != _withRelations.get(cteKey))
                                Query.parseError(errors, aliasKey.getName() + " already used as CTE name.", expr);
                            else
                            {
                                WithInfo withInfo = addWithInfo(cteKey, legalName);
                                if (null != _query.lookupWithTable(legalName))
                                    Query.parseError(errors, aliasKey + " was specified more than once in With.", expr);
                                else
                                {
                                    _query.setParsingWith(true);
                                    QueryTableWith queryTable = new QueryTableWith(_query, _query.getSchema(), legalName, cteKey, withInfo.getCteToken());
                                    _query.putWithTable(legalName, queryTable);

                                    // Build relations after creating QueryTable to handle recursive queries
                                    QueryRelation withTermRelation = Query.createQueryRelation(_query, expr, false);
                                    if (null != withTermRelation && 0 == _query.getParseErrors().size())
                                    {
                                        withInfo.setRelation(withTermRelation);
                                        TableInfo tableInfo = withTermRelation.getTableInfo();
                                        if (null != tableInfo)           // Getting table can reveal parse errors
                                            queryTable.setTableInfo(tableInfo);
                                        queryTable.setParsingWith(false);       // Done parsing the query itself
                                    }
                                    else
                                    {
                                        _query.removeWithTable(legalName);
                                    }
                                    _query.setParsingWith(false);
                                    _query.setWithFirstTerm(null);
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

    private SQLFragment getQueryWithSql(String cteKey)
    {
        SqlDialect dialect = _query.getSchema().getDbSchema().getSqlDialect();
        WithInfo withInfo = getWithRelations().get(cteKey);
        if (null == withInfo)
            throw new IllegalStateException("Expected CTE.");
        withInfo.setCTE(dialect.isWithRecursiveKeywordRequired() && _query.hasRecursiveWith());
        return withInfo._sql;
    }

    private WithInfo addWithInfo(String cteKey, String legalName)
    {
        WithInfo withInfo = new WithInfo(cteKey, legalName);
        _withRelations.put(cteKey, withInfo);
        return withInfo;
    }

    private Map<String, WithInfo> getWithRelations()
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
        final private String _cteKey;
        final private String _cteToken;

        public QueryTableWith(Query query, QuerySchema schema, String alias, String cteKey, String cteToken)
        {
            super(query, schema, alias);
            _cteKey = cteKey;
            _cteToken = cteToken;
        }

        @Override
        public SQLFragment getFromSql()
        {
            return getFromSql(getAlias());
        }

        // see QuerySelect.QueryWithWrapper
        public SQLFragment getFromSql(String alias)
        {
            // Reference to With
            _generateSelectSQL = true;
            SQLFragment sql = new SQLFragment()
                    .append(_cteToken).append(" ")
                    .append(alias);

            if (!_parsingWith)
                sql.prepend(getQueryWithSql(_cteKey));

            return sql;
        }

        @Override
        public void setContainerFilter(ContainerFilter containerFilter)
        {
            if (!_settingContainerFilter)
            {
                _settingContainerFilter = true;
                super.setContainerFilter(containerFilter);

                // Need to regenerate WITH SQL
                getWithRelations().get(_cteKey).clearCTESet();
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

    private static class WithInfo
    {
        private QueryRelation _relation = null;
        private final SQLFragment _sql;
        private final String _cteToken;
        private final String _cteKey;
        private boolean _isCTESet = false;

        WithInfo(String cteKey, String legalName)
        {
            _sql = new SQLFragment();
            _cteKey = cteKey;
            _cteToken = _sql.createCommonTableExpressionToken(cteKey, legalName);
        }

        public void setCTE(boolean isRecursive)
        {
            if (!_isCTESet)
            {
                _isCTESet = true;       // Set first to prevent endless recursion
                _sql.setCommonTableExpressionSql(_cteKey, _relation.getSql(), isRecursive);
            }
        }

        public SQLFragment getSql()
        {
            return _sql;
        }

        public String getCteToken()
        {
            return _cteToken;
        }

        public void setRelation(QueryRelation relation)
        {
            _relation = relation;
        }

        public void clearCTESet()
        {
            _isCTESet = false;
        }
    }
}
