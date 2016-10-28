/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.util.Pair;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class QOrder extends QNode
{
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("\nORDER BY ");
        boolean fComma = false;
        for (QNode node : children())
        {
            switch (node.getTokenType())
            {
                default:
                    if (fComma)
                    {
                        builder.append(",");
                    }
                    fComma = true;
                    node.appendSource(builder);
                    break;
                case SqlBaseParser.ASCENDING:
                    break;
                case SqlBaseParser.DESCENDING:
                    builder.append(" DESC");
                    break;
            }
        }
        builder.popPrefix();
    }

    public List<Map.Entry<QExpr, Boolean>> getSort()
    {
        List<Map.Entry<QExpr, Boolean>> ret = new ArrayList();
        Pair<QExpr,Boolean> entry = null;
        for (QNode child : children())
        {
            switch (child.getTokenType())
            {
                default:
                    if (entry != null)
                    {
                        ret.add(entry);
                    }
                    entry = new Pair(child, Boolean.TRUE);
                    break;
                case SqlBaseParser.DESCENDING:
                    assert entry != null;
                    entry.setValue(Boolean.FALSE);
                    break;
                case SqlBaseParser.ASCENDING:
                    assert entry != null;
                    entry.setValue(Boolean.TRUE);
                    break;
            }
        }
        if (entry != null)
        {
            ret.add(entry);
        }
        return ret;
    }

    public Map<FieldKey, Boolean> getOrderByMap()
    {
        LinkedHashMap<FieldKey, Boolean> ret = new LinkedHashMap();
        List<Map.Entry<QExpr, Boolean>> list = getSort();
        for (Map.Entry<QExpr, Boolean> entry : list)
        {
            FieldKey fieldKey = entry.getKey().getFieldKey();
            if (fieldKey == null)
            {
                continue;
            }
            ret.put(fieldKey, entry.getValue());
        }
        return ret;
    }

    public void addOrderByClause(QExpr expr, boolean ascending)
    {
        appendChild(expr);
        if (!ascending)
        {
            QNode desc = new QUnknownNode();
            desc.setTokenType(SqlBaseParser.DESCENDING);
            appendChild(desc);
        }
    }
}
