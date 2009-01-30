/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.Table;
import org.labkey.api.query.QueryParseException;

import java.util.List;
import java.util.LinkedList;


public class QUnion extends QExpr
{
    public QUnion()
    {
		super(QNode.class);
    }


    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("(");
        for (QNode node : children())
        {
            node.appendSource(builder);
			builder.popPrefix();
			builder.pushPrefix(") UNION (");
        }
        builder.append(")");
    }

    public void appendSql(SqlBuilder builder)
    {
		builder.pushPrefix("(");
		for (QNode node : children())
		{
			((QExpr)node).appendSql(builder);
			builder.popPrefix();
			builder.pushPrefix(") UNION (");
		}
		builder.append(")");
    }

}