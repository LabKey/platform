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

public class QWhere extends QNode
{
    boolean _having = false;

    public QWhere()
    {
		super(QExpr.class);
    }

    public QWhere(boolean having)
    {
		super(QExpr.class);
        _having = having;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix(_having ? "\nHAVING" :"\nWHERE ");
        for (QNode n : children())
        {
            assert null != n;
            if (null == n)
                continue;
			QExpr child = (QExpr)n;
            boolean fParen = Operator.and.needsParentheses(child, child == getFirstChild());
            if (fParen)
            {
                builder.pushPrefix("(");
            }
            child.appendSource(builder);
            if (fParen)
            {
                builder.popPrefix(")");
            }
            builder.nextPrefix(" AND\n");
        }
        builder.popPrefix();
    }




}
