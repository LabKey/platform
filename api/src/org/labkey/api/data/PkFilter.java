/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.NotFoundException;

import java.util.List;

/**
 * Helper wrapper over a standard SimpleFilter that sets up a filter on the table's primary key.
 * User: arauch
 * Date: Jan 11, 2005
 */
public class PkFilter extends SimpleFilter
{
    public PkFilter(TableInfo tinfo, Object pkVal)
    {
        super();
        List<ColumnInfo> columnPK = tinfo.getPkColumns();
        Object[] pkVals;

        assert columnPK.size() == 1 || ((Object[]) pkVal).length == columnPK.size();

        if (columnPK.size() == 1 && !pkVal.getClass().isArray())
            pkVals = new Object[]{pkVal};
        else
            pkVals = (Object[]) pkVal;

        for (int i = 0; i < pkVals.length; i++)
        {
            FieldKey fieldKey = columnPK.get(i).getFieldKey();
            Object value = pkVals[i];
            if (value instanceof String)
            {
                // If our column is not a string, and we have a string value,
                // we need to convert or Postgres 8.3 gets angry. See bug 6337
                Class<?> targetClass = columnPK.get(i).getJavaClass();
                if (targetClass != String.class)
                {
                    try
                    {
                        value = ConvertUtils.convert((String)value, targetClass);
                    }
                    catch (ConversionException e)
                    {
                        throw new NotFoundException("Failed to convert '" + value + "' for '" + fieldKey + "', should be of type " + targetClass.getName());
                    }
                }
            }
            addCondition(fieldKey, value);
        }
    }
}
