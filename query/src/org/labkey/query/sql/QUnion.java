/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.antlr.runtime.tree.CommonTree;
import org.labkey.query.sql.antlr.SqlBaseParser;


public class QUnion extends QExpr
{
    QueryUnion _union;

    public QUnion(CommonTree node)
    {
		super(QNode.class);
        from(node);
    }


    QueryUnion getQueryUnion()
    {
        return _union;
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("(");
        for (QNode node : children())
        {
            node.appendSource(builder);
			builder.popPrefix();
            builder.pushPrefix(") " + SqlParser.tokenName(getTokenType()) + " (");
        }
        builder.append(")");
    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        if (_union == null)
        {
            throw new IllegalStateException("Fields should have been resolved");
        }
        builder.append("(");
        builder.append(_union.getSql());
        builder.append(")");
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }
}