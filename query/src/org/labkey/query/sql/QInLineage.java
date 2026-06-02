/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.query.QueryServiceImpl;

import java.util.Objects;

import static org.labkey.query.sql.antlr.SqlBaseParser.EXPANCESTORSOF;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPDESCENDANTSOF;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPLINEAGEOF;

final public class QInLineage extends QExpr
{
    final boolean _in;
    final boolean _children;
    final boolean _parents;
    final String _method;

    public QInLineage(boolean in, int methodTokenType)
    {
        super(QNode.class);

        _in = in;
        _method = switch (methodTokenType)
        {
            case EXPANCESTORSOF -> {
                _children = false;
                _parents = true;
                yield "EXPANCESTORSOF";
            }
            case EXPDESCENDANTSOF -> {
                _children = true;
                _parents = false;
                yield "EXPDESCENDANTSOF";
            }
            case EXPLINEAGEOF -> {
                _children = true;
                _parents = true;
                yield "EXPLINEAGEOF";
            }
            default -> throw new IllegalArgumentException("Invalid QInLineage method token type: " + methodTokenType);
        };
    }

    String operator()
    {
        return (_in ? " IN " : " NOT IN ") + _method + " ";
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        var children = childList();
        var LHS = (QExpr) firstOrThrow(children);
        var RHS = (QQuery) secondOrThrow(children);

        // LHS should be a 'lineage object', e.g., the result of calling {ExtTable}.expObject()
        ColumnInfo lhsCol = null;
        if (LHS instanceof QueryServiceImpl.QColumnInfo || LHS instanceof QMethodCall)
        {
            SQLTableInfo sqlti = new SQLTableInfo(query.getSchema().getDbSchema(), "_");
            lhsCol = LHS.createColumnInfo(sqlti, "_", query);
        }
        if (lhsCol == null || !Strings.CS.equals(lhsCol.getConceptURI(), BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI))
        {
            query.getParseErrors().add(new QueryParseException(_method + " requires argument to be a lineage object", null, getLine(), getColumn()));
            return;
        }

        // RHS should be SELECT with one column of 'lineage object', e.g., the result of calling {ExtTable}.expObject()
        QueryRelation r = RHS._select;
        var map = r.getAllColumns();
        var col = map.size() != 1 ? null : map.values().iterator().next();
        BaseColumnInfo rhsCol = new BaseColumnInfo("_", JdbcType.INTEGER);
        if (null != col)
            col.copyColumnAttributesTo(rhsCol);
        if (!Strings.CS.equals(rhsCol.getConceptURI(), BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI))
        {
            query.getParseErrors().add(new QueryParseException(_method + " requires argument to be a lineage object", null, getLine(), getColumn()));
            return;
        }

        SqlBuilder subquery = new SqlBuilder(builder.getDialect());
        RHS.appendSql(subquery, query);
        // subquery will have surrounding parens, but the double parens don't cause a problem

        // Parse depth argument
        int depth = 1_000;
        {
            QNode depthExpr = child(children, 2);
            if (depthExpr != null)
            {
                if (!(depthExpr instanceof QNumber n))
                {
                    query.getParseErrors().add(new QueryParseException(_method + " requires second argument to be an integer", null, getLine(), getColumn()));
                    return;
                }

                depth = n.getValue().intValue();
            }
        }

        ExpLineageOptions options = new ExpLineageOptions(_parents, _children, depth);
        options.setUseObjectIds(true);          // expObject() returns objectid not lsid
        options.setOnlySelectObjectId(true);    // generate one column SELECT, also don't join to material/data/protocolapplication
        SQLFragment lineage = ExperimentService.get().generateExperimentTreeSQL(subquery, options);

        builder.append("((");
        LHS.appendSql(builder, query);
        builder.append(")");
        builder.append(_in ? " IN (" : " NOT IN (");
        builder.append(lineage);
        builder.append("))");
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("(");
        for (QNode n : children())
        {
            QExpr child = (QExpr)n;
            builder.pushPrefix("(");
            child.appendSource(builder);
            builder.popPrefix(")");
            builder.nextPrefix(operator());
        }
        builder.popPrefix(")");
    }

    @Override @NotNull
    public JdbcType getJdbcType()
    {
        return JdbcType.BOOLEAN;
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QInLineage o && _in == o._in && Objects.equals(_method, o._method);
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }
}