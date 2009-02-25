/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;

import java.util.List;

/**
 * User: arauch
 * Date: Jan 11, 2005
 * Time: 8:05:38 AM
 */
public class PkFilter extends SimpleFilter
{
    public PkFilter(TableInfo tinfo, Object pkVal)
    {
        super();
        List<ColumnInfo> columnPK = tinfo.getPkColumns();
        Object[] pkVals;

        assert null != columnPK;
        assert columnPK.size() == 1 || ((Object[]) pkVal).length == columnPK.size();

        if (columnPK.size() == 1 && !pkVal.getClass().isArray())
            pkVals = new Object[]{pkVal};
        else
            pkVals = (Object[]) pkVal;

        for (int i = 0; i < pkVals.length; i++)
        {
            String name = columnPK.get(i).getAlias();
            Object value = pkVals[i];
            if (value instanceof String)
            {
                // If our column is not a string, and we have a string value,
                // we need to convert or Postgres 8.3 gets angry. See bug 6337
                Class<?> targetClass = columnPK.get(i).getJavaClass();
                if (targetClass != String.class)
                {
                    value = ConvertUtils.convert((String)value, targetClass);
                }
            }
            addCondition(name, value);
        }
    }
}
