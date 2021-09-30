/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.DateUtil;

/**
 * Container-specific configuration settings, primarily related to look-and-feel or parsing options
 * that are not necessarily consistent across an entire project.
 * User: adam
 * Date: Aug 1, 2008
 */
public class LookAndFeelFolderProperties extends AbstractWriteableSettingsGroup
{
    static final String LOOK_AND_FEEL_SET_NAME = "LookAndFeel";

    protected static final String DEFAULT_DATE_FORMAT = "defaultDateFormatString";
    protected static final String DEFAULT_DATE_TIME_FORMAT = "defaultDateTimeFormatString";
    protected static final String RESTRICTED_COLUMNS_ENABLED = "restrictedColumnsEnabled";
    protected static final String DEFAULT_NUMBER_FORMAT = "defaultNumberFormatString";
    protected static final String EXTRA_DATE_PARSING_PATTERN = "extraDateParsingPattern";
    protected static final String EXTRA_DATE_TIME_PARSING_PATTERN = "extraDateTimeParsingPattern";

    protected final Container _c;

    protected LookAndFeelFolderProperties(Container c)
    {
        _c = c;
    }

    @Override
    protected String getType()
    {
        return "look and feel settings";
    }

    @Override
    protected String getGroupName()
    {
        return LOOK_AND_FEEL_SET_NAME;
    }


    public boolean isPropertyInherited(Container c, String name)
    {
        String value = super.lookupStringValue(c, name, null);
        return null == value;
    }


    @Override
    protected String lookupStringValue(String name, @Nullable String defaultValue)
    {
        throw new IllegalStateException("Must provide a container");
    }

    @Override
    protected String lookupStringValue(Container c, String name, @Nullable String defaultValue)
    {
        if (c.isRoot())
            return super.lookupStringValue(c, name, defaultValue);

        String value = super.lookupStringValue(c, name, null);

        if (null == value)
            value = lookupStringValue(c.getParent(), name, defaultValue);

        return value;
    }

    // TODO: consider enforcing usage of lookupBooleanValue with container
    /*@Override
    protected boolean lookupBooleanValue(String name, boolean defaultValue)
    {
        throw new IllegalStateException("Must provide a container");
    }*/

    public String getDefaultDateFormat()
    {
        // Look up this value starting from the current container (unlike most look & feel settings)
        return lookupStringValue(_c, DEFAULT_DATE_FORMAT, DateUtil.getStandardDateFormatString());
    }

    public String getDefaultDateTimeFormat()
    {
        // Look up this value starting from the current container (unlike most look & feel settings)
        return lookupStringValue(_c, DEFAULT_DATE_TIME_FORMAT, DateUtil.getStandardDateTimeFormatString());
    }

    public String getDefaultNumberFormat()
    {
        // Look up this value starting from the current container (unlike most look & feel settings)
        return lookupStringValue(_c, DEFAULT_NUMBER_FORMAT, null);
    }

    public String getExtraDateParsingPattern()
    {
        // Look up this value starting from the current container (unlike most look & feel settings)
        return lookupStringValue(_c, EXTRA_DATE_PARSING_PATTERN, null);
    }

    public String getExtraDateTimeParsingPattern()
    {
        // Look up this value starting from the current container (unlike most look & feel settings)
        return lookupStringValue(_c, EXTRA_DATE_TIME_PARSING_PATTERN, null);
    }

    public boolean areRestrictedColumnsEnabled()
    {
        return lookupBooleanValue(_c, RESTRICTED_COLUMNS_ENABLED, false);
    }

    // Get the value that's actually stored in this container; don't look up the hierarchy. This is useful only for export.
    public String getDefaultDateFormatStored()
    {
        return super.lookupStringValue(_c, DEFAULT_DATE_FORMAT, null);
    }

    // Get the value that's actually stored in this container; don't look up the hierarchy. This is useful only for export.
    public String getDefaultDateTimeFormatStored()
    {
        return super.lookupStringValue(_c, DEFAULT_DATE_TIME_FORMAT, null);
    }

    // Get the value that's actually stored in this container; don't look up the hierarchy. This is useful only for export.
    public String getDefaultNumberFormatStored()
    {
        return super.lookupStringValue(_c, DEFAULT_NUMBER_FORMAT, null);
    }

    // Get the value that's actually stored in this container; don't look up the hierarchy. This is useful only for export.
    public String getExtraDateParsingPatternStored()
    {
        return super.lookupStringValue(_c, EXTRA_DATE_PARSING_PATTERN, null);
    }

    // Get the value that's actually stored in this container; don't look up the hierarchy. This is useful only for export.
    public String getExtraDateTimeParsingPatternStored()
    {
        return super.lookupStringValue(_c, EXTRA_DATE_TIME_PARSING_PATTERN, null);
    }
}