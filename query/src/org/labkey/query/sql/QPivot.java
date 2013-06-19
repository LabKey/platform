/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

import java.util.List;

/**
 * User: matthewb
 * Date: Jan 12, 2011
 * Time: 11:26:17 AM
 */
public class QPivot extends QNode
{
    public void appendSource(SourceBuilder builder)
    {
        builder.append("PIVOT ");
        List<QNode> children = childList();

        if (children.size() < 1)
            return;
        QNode exprList = children.get(0);
        // NO PARENS
        for (QNode child : exprList.children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",\n");
        }

        if (children.size() < 2)
            return;
        QNode node = children.get(1);
        if (node instanceof QExprList)
        {
            // NO PARENS
            for (QNode child : exprList.children())
            {
                child.appendSource(builder);
                builder.nextPrefix(",\n");
            }
        }
        else
        {
            node.appendSource(builder);
        }

        if (children.size() < 3)
            return;
        builder.append(" IN " );
        exprList = children.get(2);
        // PARENS OK
        exprList.appendSource(builder);
    }
}
