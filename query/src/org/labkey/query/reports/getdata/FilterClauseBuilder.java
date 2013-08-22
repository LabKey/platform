/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;

import java.util.Collections;

/**
 * User: jeckels
 * Date: 5/21/13
 */
public class FilterClauseBuilder
{
    private FieldKey _fieldKey;
    private CompareType _type;
    private Object _value;

    public void setFieldKey(FieldKey fieldKey)
    {
        _fieldKey = fieldKey;
    }

    public void setType(String typeName)
    {
        // First check based on URL name
        for (CompareType compareType : CompareType.values())
        {
            if (compareType.getUrlKeys().contains(typeName))
            {
                _type = compareType;
                break;
            }
        }
        if (_type == null)
        {
            // If no match, check based on the enum name itself
            for (CompareType compareType : CompareType.values())
            {
                if (compareType.toString().equalsIgnoreCase(typeName))
                {
                    _type = compareType;
                    break;
                }
            }
        }
        if (_type == null)
        {
            // If still no match, check based on the verbose text
            for (CompareType compareType : CompareType.values())
            {
                if (compareType.getDisplayValue().equalsIgnoreCase(typeName))
                {
                    _type = compareType;
                    break;
                }
            }
        }
        if (_type == null)
        {
            throw new IllegalArgumentException("Could not resolve filter type: '" + typeName + "'");
        }
    }

    public void setValue(Object value)
    {
        _value = value;
    }

    public void append(SimpleFilter filter)
    {
        if (_type == null)
        {
            throw new IllegalStateException("No type specified for filter");
        }
        if (_fieldKey == null)
        {
            throw new IllegalStateException("No fieldKey specified for filter");
        }
        filter.addCondition(_fieldKey, _value, _type);
    }

    @Override
    public String toString()
    {
        return "FilterClauseBuilder{" +
                "fieldKey=" + _fieldKey +
                ", type=" + _type +
                ", value=" + _value +
                '}';
    }

    public static class TestCase extends Assert
    {
        private String toLabKeySQL(CompareType type, Object value, JdbcType jdbcType)
        {
            FilterClauseBuilder builder = new FilterClauseBuilder();
            builder.setFieldKey(FieldKey.fromParts("Field1"));
            builder.setType(type.getPreferredUrlKey());
            builder.setValue(value);

            SimpleFilter filter = new SimpleFilter();
            builder.append(filter);
            ColumnInfo column = new ColumnInfo(builder._fieldKey);
            column.setJdbcType(jdbcType);
            return filter.toLabKeySQL(Collections.singletonMap(builder._fieldKey, column));
        }

        @Test
        public void testStartsWithToSQL()
        {
            assertEquals("(STARTSWITH(\"Field1\", 'value'))", toLabKeySQL(CompareType.STARTS_WITH, "value", JdbcType.VARCHAR));
        }

        @Test
        public void testNullToSQL()
        {
            assertEquals("(\"Field1\" IS NULL)", toLabKeySQL(CompareType.ISBLANK, null, JdbcType.VARCHAR));
        }

        @Test
        public void testInClauseToSQL()
        {
            assertEquals("(((\"Field1\" IN ('value1', 'value2'))))", toLabKeySQL(CompareType.IN, "value1;value2", JdbcType.VARCHAR));
        }
    }
}
