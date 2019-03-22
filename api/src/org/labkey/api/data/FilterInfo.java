/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.query.FieldKey;
import org.labkey.api.util.URLHelper;

import java.io.Serializable;

/**
 * Bean to capture a single filter on a single column.
 * User: adam
 * Date: Jun 2, 2009
 */
public class FilterInfo implements Serializable
{
    final FieldKey field;
    final CompareType op;
    final String value;

    public FilterInfo(String field, String op, String value)
    {
        this(FieldKey.fromString(field), CompareType.getByURLKey(op), value);
    }

    public FilterInfo(FieldKey field, CompareType op, String value)
    {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    public FieldKey getField()
    {
        return field;
    }

    public CompareType getOp()
    {
        return op;
    }

    public String getValue()
    {
        return value;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilterInfo that = (FilterInfo) o;

        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (op != that.op) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = (field != null ? field.hashCode() : 0);
        result = 31 * result + (op != null ? op.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    /**
     * Add the filter parameter on the url
     * @param url The url to be modified.
     * @param regionName The dataRegion used to scope the sort.
     * @param fieldKey The fieldKey to use in the url parameter
     */
    public void applyToURL(URLHelper url, String regionName, FieldKey fieldKey)
    {
        String opStr = op.getPreferredUrlKey() != null ? op.getPreferredUrlKey() : "";
        String valueStr = value != null ? value : "";
        url.addParameter(regionName + "." + fieldKey.toString() + "~" + opStr, valueStr);
    }
}
