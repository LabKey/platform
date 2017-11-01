/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.issue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: 6/30/12
 * Time: 8:41 PM
 */


@Deprecated // This class can be deleted in 19.1
public enum ColumnTypeEnum implements ColumnType
{
    INT1(0, "int1", false),
    INT2(0, "int2", false),
    AREA(1, "area", true),
    TYPE(2, "type", true),
    MILESTONE(3, "milestone", true),
    PRIORITY(6, "priority", true)
    {
        @NotNull
        @Override
        public String[] getInitialValues()
        {
            return new String[]{"0", "1", "2", "3", "4"};
        }

        @NotNull
        @Override
        public String getInitialDefaultValue()
        {
            return "3";
        }
    },
    RESOLUTION(7, "resolution", false)
    {
        @NotNull
        @Override
        public String[] getInitialValues()
        {
            return new String[]{"Fixed", "Duplicate", "Won't Fix", "Not Repro", "By Design"};
        }

        @NotNull
        @Override
        public String getInitialDefaultValue()
        {
            return "Fixed";
        }
    },
    STRING1(4, "string1", false),
    STRING2(5, "string2", false),
    STRING3(8, "string3", false),
    STRING4(9, "string4", false),
    STRING5(10, "string5", false),
    RELATED(11, "related", false);

    private final int _ordinal;            // Ordinal used for storing/retrieving associated keywords in database (same as old int fake enum)
    private final String _columnName;      // Standard database column name (same as old static final string)... these names never need HTML encoding
    private final boolean _standard;       // Is this one of the four "standard" columns? (Type, Area, Priority, or Milestone)
    private final boolean _customString;   // Is this a custom String column?
    private final boolean _customInteger;  // Is this a custom Integer column?

    private static final Map<String, ColumnTypeEnum> mapByColumnName = new CaseInsensitiveHashMap<>(15);
    private static final Map<Integer, ColumnTypeEnum> mapByOrdinal = new HashMap<>(15);
    private static final String[] noKeywords = new String[0];

    static
    {
        for (ColumnTypeEnum type : values())
        {
            mapByColumnName.put(type.getColumnName(), type);
            mapByOrdinal.put(type.getOrdinal(), type);
        }
    }

    ColumnTypeEnum(int ordinal, String columnName, boolean standard)
    {
        _ordinal = ordinal;
        _columnName = columnName;
        _standard = standard;
        _customString = columnName.startsWith("string");
        _customInteger = columnName.startsWith("int");
    }

    public int getOrdinal()
    {
        return _ordinal;
    }

    public String getColumnName()
    {
        return _columnName;
    }

    public boolean isStandard()
    {
        return _standard;
    }

    public boolean isCustom()
    {
        return _customString || _customInteger;
    }

    public @NotNull String[] getInitialValues()
    {
        return noKeywords;
    }

    public @NotNull String getInitialDefaultValue()
    {
        throw new IllegalStateException("Initial default value must be specified iff getInitialValues() is implemented");
    }

    public static @Nullable
    ColumnTypeEnum forName(String columnName)
    {
        return mapByColumnName.get(columnName);
    }

    public static @Nullable
    ColumnTypeEnum forOrdinal(int ordinal)
    {
        return mapByOrdinal.get(ordinal);
    }
}
