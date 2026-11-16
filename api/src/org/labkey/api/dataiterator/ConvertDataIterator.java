/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;

/**
 * Converts each matched column's values to its target column's type, handling missing values and remapping lookup
 * values to the lookup target's key; unmatched columns pass through.
 */
public class ConvertDataIterator extends SimpleTranslator
{
    public ConvertDataIterator(MatchMakerDataIterator input, DataIteratorContext context, @Nullable Container mvContainer)
    {
        super(input, context);
        setMvContainer(mvContainer);

        for (int i = 1; i <= input.getColumnCount(); i++)
        {
            MatchMakerDataIterator.TargetColumn match = input.getMatch(i);
            if (null == match)
            {
                // pass through unrecognized columns (may be internal column like "_key")
                addColumn(i);
                continue;
            }

            ColumnInfo col = match.getTarget();
            DomainProperty dp = match.getDp();
            PropertyType pt = null == dp ? null : dp.getPropertyDescriptor().getPropertyType();
            assert null == pt || null == col.getPropertyType() || col.getPropertyType() == pt;

            // Issue 53498: attachment is blank after update from file, if the field name contains underscore
            if (pt == PropertyType.ATTACHMENT || pt == PropertyType.FILE_LINK)
                addColumn(col, i);
            else
                addConvertColumn(col, i, match.getIndexMv(), col.getRemapMissingBehavior(), context.isWithLookupRemapping());
        }
    }
}
