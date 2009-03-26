/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.exp;

import org.labkey.api.query.AliasedColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;

/**
 * Display column for holding the raw value for a qc-enabled field.
 *
 * User: jgarms
 * Date: Mar 10, 2009
 */
public class RawValueColumn extends AliasedColumn
{
    public static final String RAW_VALUE_SUFFIX = "RawValue";

    public RawValueColumn(TableInfo table, ColumnInfo valueColumn)
    {
        super(table, valueColumn.getName() + RAW_VALUE_SUFFIX, valueColumn);
        setCaption(getName());
        setUserEditable(false);
        setIsHidden(true);
        setQcColumnName(null); // This column itself does not allow QC
        setNullable(true); // Otherwise we get complaints on import for required fields
    }
}

