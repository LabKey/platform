/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.column.BuiltInColumnTypes;


final public class QInLineage extends QExpr
{
    final boolean _in;
    final boolean _parents;

    public QInLineage(boolean in, boolean parents)
    {
        this._in = in;
        this._parents = parents;
    }

    String operator()
    {
        return (_in ? " IN " : " NOT IN ") + (_parents ? "EXPANCESTORSOF " : "EXPDESCENDANTSOF " );
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        SQLTableInfo sqlti = new SQLTableInfo(query.getSchema().getDbSchema(), "_");
        var children = childList();
        var LHS = ((QExpr) getFirstChild());
        var RHS = ((QQuery) getLastChild());

        // LHS should be a 'lineage object', e.g. the result of calling {ExtTable}.expObject()
        ColumnInfo lhsCol = !(LHS instanceof QMethodCall) ? null : LHS.createColumnInfo(sqlti, "_", query);
        if (lhsCol == null || !StringUtils.equals(lhsCol.getConceptURI(), BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI))
        {
            query.getParseErrors().add(new QueryParseException(operator() + " requires argument to be a lineage object", null, getLine(), getColumn()));
            return;
        }

        // RHS should be SELECT with one column of 'lineage object', e.g. the result of calling {ExtTable}.expObject()
        QueryRelation r = RHS._select;
        var map = r.getAllColumns();
        var col = map.size() != 1 ? null : map.values().iterator().next();
        BaseColumnInfo rhsCol = new BaseColumnInfo("_", JdbcType.INTEGER);
        if (null != col)
            col.copyColumnAttributesTo(rhsCol);
        if (!StringUtils.equals(rhsCol.getConceptURI(), BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI))
        {
            query.getParseErrors().add(new QueryParseException(operator() + " requires argument to be a lineage object", null, getLine(), getColumn()));
            return;
        }

        SqlBuilder subquery = new SqlBuilder(builder.getDialect());
        RHS.appendSql(subquery, query);
        // subquery will have surrounding parens, but the double parens don't cause a problem

        ExpLineageOptions options = new ExpLineageOptions(_parents, !_parents, 1000);
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
        return (other instanceof QInLineage o) && _in == o._in && _parents == o._parents;
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }
}