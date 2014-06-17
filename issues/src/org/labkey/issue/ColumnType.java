/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.issue.model.Issue;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * User: adam
 * Date: 6/30/12
 * Time: 8:41 PM
 */
public enum ColumnType
{
    INT1(0, "int1", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            throw new IllegalStateException("Shouldn't be setting value for column type INT1");
        }

        @Override
        public String getValue(Issue issue)
        {
            if(issue.getInt1() != null)
                return String.valueOf(issue.getInt1());
            else
                return null;
        }
    },
    INT2(0, "int2", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            throw new IllegalStateException("Shouldn't be setting value for column type INT2");
        }

        @Override
        public String getValue(Issue issue)
        {
            if(issue.getInt2() != null)
                return String.valueOf(issue.getInt2());
            else
                return null;
        }
    },
    AREA(1, "area", true)
    {
        @Override
        public String getValue(Issue issue)
        {
            return issue.getArea();
        }

        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setArea(value);
        }
    },
    TYPE(2, "type", true)
    {
        @Override
        public String getValue(Issue issue)
        {
            return issue.getType();
        }

        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setType(value);
        }
    },
    MILESTONE(3, "milestone", true)
    {
        @Override
        public String getValue(Issue issue)
        {
            return issue.getMilestone();
        }

        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setMilestone(value);
        }
    },
    PRIORITY(6, "priority", true)
    {
        @Override
        public String getValue(Issue issue)
        {
            if(issue.getPriority() != null)
                return String.valueOf(issue.getPriority());
            else
                return null;
        }

        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setPriority(null != value ? Integer.parseInt(value) : 3);
        }

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

        @Override
        public boolean allowBlank()
        {
            return false;
        }
    },
    RESOLUTION(7, "resolution", false)
    {
        @Override
        public String getValue(Issue issue)
        {
            return issue.getResolution();
        }

        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setResolution(value);
        }

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

        @Override
        public boolean allowBlank()
        {
            return false;
        }
    },
    STRING1(4, "string1", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setString1(value);
        }

        @Override
        public String getValue(Issue issue)
        {
            return issue.getString1();
        }
    },
    STRING2(5, "string2", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setString2(value);
        }

        @Override
        public String getValue(Issue issue)
        {
            return issue.getString2();
        }
    },
    STRING3(8, "string3", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setString3(value);
        }

        @Override
        public String getValue(Issue issue)
        {
            return issue.getString3();
        }
    },
    STRING4(9, "string4", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setString4(value);
        }

        @Override
        public String getValue(Issue issue)
        {
            return issue.getString4();
        }
    },
    STRING5(10, "string5", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setString5(value);
        }

        @Override
        public String getValue(Issue issue)
        {
            return issue.getString5();
        }
    },
    RELATED(11, "related", false)
    {
        @Override
        public void setValue(Issue issue, String value)
        {
            issue.setRelated(value);
        }

        @Override
        public String getValue(Issue issue)
        {
            return issue.getRelated();
        }
    };

    private final int _ordinal;            // Ordinal used for storing/retrieving associated keywords in database (same as old int fake enum)
    private final String _columnName;      // Standard database column name (same as old static final string)... these names never need HTML encoding
    private final boolean _standard;       // Is this one of the four "standard" columns? (Type, Area, Priority, or Milestone)
    private final boolean _customString;   // Is this a custom String column?
    private final boolean _customInteger;  // Is this a custom Integer column?

    private static final Map<String, ColumnType> mapByColumnName = new CaseInsensitiveHashMap<>(15);
    private static final Map<Integer, ColumnType> mapByOrdinal = new HashMap<>(15);
    private static final Collection<ColumnType> customStringColumns = new LinkedList<>();
    private static final String[] noKeywords = new String[0];

    static
    {
        for (ColumnType type : values())
        {
            mapByColumnName.put(type.getColumnName(), type);
            mapByOrdinal.put(type.getOrdinal(), type);

            if (type.isCustomString())
                customStringColumns.add(type);
        }
    }

    ColumnType(int ordinal, String columnName, boolean standard)
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

    public boolean isCustomString()
    {
        return _customString;
    }

    public boolean isCustomInteger()
    {
        return _customInteger;
    }

    public boolean isCustom()
    {
        return _customString || _customInteger;
    }

    // Most pick lists display a blank entry
    public boolean allowBlank()
    {
        return true;
    }

    public @NotNull String[] getInitialValues()
    {
        return noKeywords;
    }

    public @NotNull String getInitialDefaultValue()
    {
        throw new IllegalStateException("Initial default value must be specified iff getInitialValues() is implemented");
    }

    public abstract String getValue(Issue issue);

    public abstract void setValue(Issue issue, String value);

    // Convenience method for setting default
    public void setDefaultValue(Issue issue, Map<ColumnType, String> defaults)
    {
        setValue(issue, defaults.get(this));
    }

    public static @Nullable
    ColumnType forName(String columnName)
    {
        return mapByColumnName.get(columnName);
    }

    public static @Nullable
    ColumnType forOrdinal(int ordinal)
    {
        return mapByOrdinal.get(ordinal);
    }

    public static @NotNull Collection<ColumnType> getCustomStringColumns()
    {
        return customStringColumns;
    }
}
