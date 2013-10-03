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

abstract public class QFieldKey extends QExpr
{
    static public QFieldKey of(FieldKey key)
    {
        if (key == null)
            return null;
        QFieldKey parent = of(key.getTable());
        if (parent == null)
            return new QIdentifier(key.getName());
        if (key.isAllColumns())
        {
            return new QDot(parent, new QRowStar());
        }
        return new QDot(parent, new QIdentifier(key.getName()));
    }

    abstract public FieldKey getFieldKey();

    public void appendSql(SqlBuilder builder, Query query)
    {
        throw new IllegalStateException("Fields should have been resolved.");
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }
}
