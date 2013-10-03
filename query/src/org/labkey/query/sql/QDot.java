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

import org.labkey.api.query.FieldKey;

public class QDot extends QFieldKey
{
    public QDot()
    {
    }

    public QDot(QExpr left, QExpr right)
    {
        appendChildren(left, right);
    }

    public FieldKey getFieldKey()
    {
        FieldKey left = ((QExpr)getFirstChild()).getFieldKey();
        if (left == null)
            return null;
        FieldKey right = ((QExpr)getLastChild()).getFieldKey();
        return FieldKey.fromParts(left,right);
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("");
        for (QNode child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(".");
        }
        builder.popPrefix();
    }

    public String getValueString()
    {
        StringBuilder ret = new StringBuilder();
        boolean first = true;
        for (QNode child : children())
        {
            if (!first)
            {
                ret.append(".");
            }
            first = false;
            String strChild = ((QExpr)child).getValueString();
            if (strChild == null)
                return null;
            ret.append(strChild);
        }
        return ret.toString();
    }
}
