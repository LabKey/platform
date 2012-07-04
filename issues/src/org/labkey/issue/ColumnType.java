package org.labkey.issue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.HString;
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
    INT1(0, "int1", false, false)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            throw new IllegalStateException("Shouldn't be setting value for column type INT1");
        }

        @Override
        public HString getValue(Issue issue)
        {
            return HString.valueOf(issue.getInt1());
        }
    },
    INT2(0, "int2", false, false)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            throw new IllegalStateException("Shouldn't be setting value for column type INT2");
        }

        @Override
        public HString getValue(Issue issue)
        {
            return HString.valueOf(issue.getInt2());
        }
    },
    AREA(1, "area", true, false)
    {
        @Override
        public HString getValue(Issue issue)
        {
            return issue.getArea();
        }

        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setArea(value);
        }
    },
    TYPE(2, "type", true, false)
    {
        @Override
        public HString getValue(Issue issue)
        {
            return issue.getType();
        }

        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setType(value);
        }
    },
    MILESTONE(3, "milestone", true, false)
    {
        @Override
        public HString getValue(Issue issue)
        {
            return issue.getMilestone();
        }

        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setMilestone(value);
        }
    },
    PRIORITY(6, "priority", true, false)
    {
        @Override
        public HString getValue(Issue issue)
        {
            return HString.valueOf(issue.getPriority());
        }

        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setPriority(null != value ? value.parseInt() : 3);
        }

        @NotNull
        @Override
        public HString[] getInitialValues()
        {
            return HString.array(false, "0", "1", "2", "3", "4");
        }

        @NotNull
        @Override
        public HString getInitialDefaultValue()
        {
            return new HString("3", false);
        }

        @Override
        public boolean allowBlank()
        {
            return false;
        }
    },
    RESOLUTION(7, "resolution", false, false)
    {
        @Override
        public HString getValue(Issue issue)
        {
            return issue.getResolution();
        }

        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setResolution(value);
        }

        @NotNull
        @Override
        public HString[] getInitialValues()
        {
            return HString.array(false, "Fixed", "Duplicate", "Won't Fix", "Not Repro", "By Design");
        }

        @NotNull
        @Override
        public HString getInitialDefaultValue()
        {
            return new HString("Fixed", false);
        }

        @Override
        public boolean allowBlank()
        {
            return false;
        }
    },
    STRING1(4, "string1", false, true)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setString1(value);
        }

        @Override
        public HString getValue(Issue issue)
        {
            return issue.getString1();
        }
    },
    STRING2(5, "string2", false, true)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setString2(value);
        }

        @Override
        public HString getValue(Issue issue)
        {
            return issue.getString2();
        }
    },
    STRING3(8, "string3", false, true)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setString3(value);
        }

        @Override
        public HString getValue(Issue issue)
        {
            return issue.getString3();
        }
    },
    STRING4(9, "string4", false, true)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setString4(value);
        }

        @Override
        public HString getValue(Issue issue)
        {
            return issue.getString4();
        }
    },
    STRING5(10, "string5", false, true)
    {
        @Override
        public void setValue(Issue issue, HString value)
        {
            issue.setString5(value);
        }

        @Override
        public HString getValue(Issue issue)
        {
            return issue.getString5();
        }
    };

    private final int _ordinal;           // Ordinal used for storing/retrieving associated keywords in database (same as old int fake enum)
    private final String _columnName;     // Standard database column name (same as old static final string)
    private final boolean _standard;      // Is this one of the four "standard" columns? (Type, Area, Priority, or Milestone)
    private final boolean _customString;  // Is this a custom string column?

    private static final Map<String, ColumnType> mapByColumnName = new CaseInsensitiveHashMap<ColumnType>(15);
    private static final Map<Integer, ColumnType> mapByOrdinal = new HashMap<Integer, ColumnType>(15);
    private static final Collection<ColumnType> customStringColumns = new LinkedList<ColumnType>();
    private static final HString[] noKeywords = new HString[0];

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

    ColumnType(int ordinal, String columnName, boolean standard, boolean customString)
    {
        _ordinal = ordinal;
        _columnName = columnName;
        _standard = standard;
        _customString = customString;
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

    // Most pick lists display a blank entry
    public boolean allowBlank()
    {
        return true;
    }

    public @NotNull HString[] getInitialValues()
    {
        return noKeywords;
    }

    public @NotNull HString getInitialDefaultValue()
    {
        throw new IllegalStateException("Initial default value must be specified iff getInitialValues() is implemented");
    }

    public abstract HString getValue(Issue issue);

    public abstract void setValue(Issue issue, HString value);

    // Convenience method for setting default
    public void setDefaultValue(Issue issue, Map<ColumnType, HString> defaults)
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
