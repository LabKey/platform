/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.BatchValidationException;

import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: 2011-08-26
 * Time: 1:42 PM
 *
 * This iterator is called prepares data for the BeforeTriggerDataIterator.
 * We do a best attempt at converting to the target type, but to not fail if
 * conversion does not work.
 *
 * Also add NULL for missing columns, so that the shape does not change before/after the trigger.
 */
public class CoerceDataIterator extends SimpleTranslator
{
    public CoerceDataIterator(DataIterator source, DataIteratorContext context, TableInfo target)
    {
        super(source, context);
        setDebugName("Coerce before trigger script");
        init(target, context.getInsertOption().useImportAliases);
    }

    void init(TableInfo target, boolean useImportAliases)
    {
        Map<String,ColumnInfo> targetMap = DataIteratorUtil.createTableMap(target, useImportAliases);
        Set<String> seen = new CaseInsensitiveHashSet();
        DataIterator di = getInput();
        int count = di.getColumnCount();
        for (int i=1 ; i<=count ; i++)
        {
            ColumnInfo from = di.getColumnInfo(i);
            ColumnInfo to = targetMap.get(from.getName());

            if (null != to)
            {
                seen.add(to.getName());
                if (to.getPropertyType() == PropertyType.ATTACHMENT)
                    addColumn(to, i);
                else if (to.getFk() instanceof MultiValuedForeignKey)
                    addColumn(to.getName(), i); // pass-through multi-value columns -- converting will stringify a collection
                else
                    addConvertColumn(to.getName(), i, to.getJdbcType(), false);
            }
            else
            {
                addColumn(i);
            }
        }
        // add null column for all insertable not seen columns
        for (ColumnInfo to : target.getColumns())
        {
            if (seen.contains(to.getName()))
                continue;
            if (!to.isUserEditable() || to.isAutoIncrement())
                continue;
            addNullColumn(to.getName(), to.getJdbcType());
        }
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        return super.next();
    }

    @Override
    public Object get(int i)
    {
        return super.get(i);
    }

    @Override
    protected Object addConversionException(String fieldName, Object value, JdbcType target, Exception x)
    {
        return value;
    }
}
