package org.labkey.core.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum DateDisplayFormatType
{
    Date(JdbcType.DATE, PropertyType.DATE)
    {
        @Override
        public boolean isStandardFormat(String formatPattern)
        {
            return DateUtil.isStandardDateDisplayFormat(formatPattern);
        }

        @Override
        String getStoredFormat(LookAndFeelProperties laf)
        {
            return laf.getDefaultDateFormatStored();
        }
    },
    DateTime(JdbcType.TIMESTAMP, PropertyType.DATE_TIME)
    {
        @Override
        public boolean isStandardFormat(String formatPattern)
        {
            return DateUtil.isStandardDateTimeDisplayFormat(formatPattern);
        }

        @Override
        String getStoredFormat(LookAndFeelProperties laf)
        {
            return laf.getDefaultDateTimeFormatStored();
        }
    },
    Time(JdbcType.TIME, PropertyType.TIME)
    {
        @Override
        public boolean isStandardFormat(String formatPattern)
        {
            return DateUtil.isStandardTimeDisplayFormat(formatPattern);
        }

        @Override
        String getStoredFormat(LookAndFeelProperties laf)
        {
            return laf.getDefaultTimeFormatStored();
        }
    };

    private static final Map<String, DateDisplayFormatType> MAP_FOR_TYPE_URI = new HashMap<>();
    private static final Map<JdbcType, DateDisplayFormatType> MAP_FOR_JDBC_TYPE = new HashMap<>();

    private final JdbcType _jdbcType;
    private final String _typeUri;

    DateDisplayFormatType(JdbcType jdbcType, PropertyType propertyType)
    {
        _jdbcType = jdbcType;
        _typeUri = propertyType.getTypeUri();
    }

    static
    {
        Arrays.stream(values()).forEach(type -> {
            MAP_FOR_TYPE_URI.put(type.getTypeUri(), type);
            MAP_FOR_JDBC_TYPE.put(type.getJdbcType(), type);
        });
    }

    public JdbcType getJdbcType()
    {
        return _jdbcType;
    }

    public String getTypeUri()
    {
        return _typeUri;
    }

    public static List<String> getTypeUris()
    {
        return Arrays.stream(values()).map(DateDisplayFormatType::getTypeUri).toList();
    }

    public static DateDisplayFormatType getForRangeUri(String rangeUri)
    {
        return MAP_FOR_TYPE_URI.get(rangeUri);
    }

    public static @Nullable DateDisplayFormatType getForJdbcType(JdbcType jdbcType)
    {
        return MAP_FOR_JDBC_TYPE.get(jdbcType);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean isStandardFormat(String formatPattern);

    abstract String getStoredFormat(LookAndFeelProperties laf);
}
