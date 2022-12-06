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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.data.xml.ColumnType;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * This class was called QueryWith. That was confusing because QuerySelect, QueryTable, QueryPivot, etc.
 * all represent SQL operations that return a relation.  This is a holder of common table expression helpers.
 */
public class CommonTableExpressions
{
    final private Map<String, WithInfo> _withRelations = new LinkedCaseInsensitiveMap<>();
    final private Query _query;
    final private SqlDialect _dialect;

    public CommonTableExpressions(@NotNull Query query, @NotNull QWith qwith)
    {
        _query = query;
        _dialect = _query.getSchema().getDbSchema().getSqlDialect();

        List<QueryException> errors = _query.getParseErrors();
        if (!_dialect.isLabKeyWithSupported())
        {
            Query.parseError(errors, "WITH not supported for " + _dialect.getProductName(), qwith);
            return;
        }
        for (var qnode : qwith.children())
        {
            if (!(qnode instanceof QAs qAs))
            {
                Query.parseError(errors, "Syntax error near WITH", qnode);
                return;
            }

            // With's QAs is backwards from other QAs's
            QNode expr = qAs.getLastChild();

            if (!(expr instanceof QQuery) && !(expr instanceof QUnion))
            {
                Query.parseError(errors, "Syntax error near WITH.", expr);
                return;
            }

            QIdentifier alias = (QIdentifier) qAs.getFirstChild();
            QTable table = new QTable((QExpr) expr, null);
            table.setAlias(alias);
            FieldKey aliasKey = table.getAlias();
            if (null == aliasKey)
            {
                Query.parseError(errors, "Syntax error near WITH.", expr);
                return;
            }

            String legalName = getLegalName(_dialect, aliasKey.getName());
            String cteKey = makeCteKey(legalName);
            if (null != _withRelations.get(cteKey))
            {
                Query.parseError(errors, aliasKey.getName() + " already used as CTE name.", expr);
                return;
            }
            if (null != _query.lookupCteTable(legalName))
            {
                Query.parseError(errors, aliasKey + " was specified more than once in With.", expr);
                return;
            }
            WithInfo withInfo = addWithInfo(cteKey, legalName);

            _query.setParsingWith(true);
            QueryTableWith queryTable = new QueryTableWith(_query, legalName, cteKey, withInfo.getCteToken());
            withInfo.setRelation(queryTable);
            // Does this mean CTE names are global?  Not sure, since we don't seem to merge these in
            // Query._resolveTable() (as with parameters), so maybe not.
            _query.putCteTable(legalName, queryTable);

            QueryRelation withTermRelation;
            if (expr instanceof QUnion qunion)
            {
                withTermRelation = new QueryUnion(query, queryTable);
                queryTable.setWrapped(withTermRelation);        // make available in global namespace for recursive binding
                ((QueryUnion)withTermRelation).setQUnion(qunion);
            }
            else
            {
                withTermRelation = Query.createQueryRelation(_query, expr, true, true);
                // NOTE createQueryRelation() may wrap result with a QueryLookupWrapper that is not needed here.  It doesn't hurt, but it's not needed.
                if (withTermRelation instanceof QueryLookupWrapper qlw)
                    withTermRelation = qlw._source;
                queryTable.setWrapped(withTermRelation);
            }

            queryTable.setParsingWith(false);
            _query.setParsingWith(false);
        }

        // Make sure all columns in CTEs are referenced (recursive references might not get handled).
        for (var withInfo : _withRelations.values())
        {
            QueryRelation relation = withInfo._queryTableWith._wrapped;
            if (relation instanceof QuerySelect select)
                select.markAllSelected(this);
        }
    }


    private SQLFragment getQueryWithSql(String cteKey)
    {
        WithInfo withInfo = getWithRelations().get(cteKey);
        if (null == withInfo)
            throw new IllegalStateException("Expected CTE.");
        withInfo.setCTE(_dialect.isWithRecursiveKeywordRequired() && _query.hasRecursiveWith());
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


    public class QueryTableWith extends QueryRelationWrapper<QueryRelation>
    {
        final private String _name;
        private boolean _seenRecursiveReference = false;
        private boolean _parsingWith = true;
        private boolean _settingContainerFilter = false;    // prevent endless recursion
        final private String _cteKey;
        final private String _cteToken;


        public QueryTableWith(Query query, String cteName, String cteKey, String cteToken)
        {
            super(query);
            _name = cteName;
            _cteKey = cteKey;
            _cteToken = cteToken;
        }

        public String getCteName()
        {
            return _name;
        }

        // CTE is shared so we don't pass through lookup requests, make QuerySelect.QueryWithWrapper handle it.
        @Override
        public @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
        {
            return null;
        }

        @Override
        public @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, ColumnType.@NotNull Fk fk, @NotNull String name)
        {
            return null;
        }

        @Override
        public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
        {
            return Set.of();
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

        /* avoid recursion! */
        boolean resolving = false;

        @Override
        public void resolveFields()
        {
            if (resolving)
                return;
            try
            {
                super.resolveFields();
            }
            finally
            {
                resolving = false;
            }
        }
    }


    private class WithInfo
    {
        QueryTableWith _queryTableWith = null;
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
                SQLFragment sqlf = _queryTableWith._wrapped.getSql();
                _sql.setCommonTableExpressionSql(_cteKey, sqlf, isRecursive);
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

        public void setRelation(QueryTableWith relation)
        {
            _queryTableWith = relation;
        }

        public void clearCTESet()
        {
            _isCTESet = false;
        }
    }
}
