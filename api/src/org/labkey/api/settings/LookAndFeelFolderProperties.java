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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.DateUtil;

import java.util.Map;

import static org.labkey.api.settings.LookAndFeelProperties.Properties.extraDateParsingPattern;
import static org.labkey.api.settings.LookAndFeelProperties.Properties.extraDateTimeParsingPattern;
import static org.labkey.api.settings.LookAndFeelProperties.Properties.extraTimeParsingPattern;
import static org.labkey.api.settings.LookAndFeelProperties.Properties.restrictedColumnsEnabled;

/**
 * Container-specific configuration settings, primarily related to look-and-feel or parsing options
 * that are not necessarily consistent across an entire project.
 */
public class LookAndFeelFolderProperties extends AbstractWriteableSettingsGroup
{
    public static final String LOOK_AND_FEEL_SET_NAME = "LookAndFeel";

    // These are the legacy property names for the format patterns
    protected static final String defaultDateFormatString = "defaultDateFormatString";
    protected static final String defaultDateTimeFormatString = "defaultDateTimeFormatString";
    protected static final String defaultNumberFormatString = "defaultNumberFormatString";
    protected static final String defaultTimeFormatString = "defaultTimeFormatString";

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

    // Get the actual stored value in this container; no default handling, no walking the hierarchy
    protected String getStoredValue(Container c, String name)
    {
        Map<String, String> props = getProperties(c);
        return props.get(name);
    }

    // Get the actual stored value in this container; no default handling, no walking the hierarchy
    protected String getStoredValue(Container c, Enum<?> e)
    {
        return getStoredValue(c, e.name());
    }

    // TODO: consider enforcing usage of lookupBooleanValue with container
    /*@Override
    protected boolean lookupBooleanValue(String name, boolean defaultValue)
    {
        throw new IllegalStateException("Must provide a container");
    }*/

    // Returns inherited value from the cache. Same as DateUtil.getDateFormatString(Container).
    public String getDefaultDateFormat()
    {
        return FolderSettingsCache.getDefaultDateFormat(_c);
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy
    // This is useful for export and showing inheritance status in the UI.
    public String getDefaultDateFormatStored()
    {
        return getStoredValue(_c, defaultDateFormatString);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getDefaultDateFormat() instead.
    public String calculateDefaultDateFormat()
    {
        // Look up this value starting from the current container
        return lookupStringValue(_c, defaultDateFormatString, DateUtil.getStandardDateFormatString());
    }

    // Returns inherited value from the cache. Same as DateUtil.getDateTimeFormatString(Container).
    public String getDefaultDateTimeFormat()
    {
        return FolderSettingsCache.getDefaultDateTimeFormat(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getDefaultDateTimeFormat() instead.
    public String calculateDefaultDateTimeFormat()
    {
        // Look up this value starting from the current container
        return lookupStringValue(_c, defaultDateTimeFormatString, DateUtil.getStandardDateTimeFormatString());
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public String getDefaultDateTimeFormatStored()
    {
        return getStoredValue(_c, defaultDateTimeFormatString);
    }

    // Returns inherited value from the cache. Same as DateUtil.getTimeFormatString(Container).
    public String getDefaultTimeFormat()
    {
        return FolderSettingsCache.getDefaultTimeFormat(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getDefaultTimeFormat() instead.
    public String calculateDefaultTimeFormat()
    {
        // Look up this value starting from the current container
        return lookupStringValue(_c, defaultTimeFormatString, DateUtil.getStandardTimeFormatString());
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public String getDefaultTimeFormatStored()
    {
        return getStoredValue(_c, defaultTimeFormatString);
    }

    // Returns inherited value from the cache. Same as Formats.getNumberFormatString(Container).
    public String getDefaultNumberFormat()
    {
        return FolderSettingsCache.getDefaultNumberFormat(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getDefaultNumberFormat() instead.
    public String calculateDefaultNumberFormat()
    {
        // Look up this value starting from the current container.
        // Note: Unchecking "Inherit" and saving an empty number format saves the empty string (""). Unfortunately,
        // new DateFormat("") happily provides a format with thousands separators (??), so without this trimToNull()
        // call, all numbers are displayed with commas, including RowIds.
        return StringUtils.trimToNull(lookupStringValue(_c, defaultNumberFormatString, null));
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public String getDefaultNumberFormatStored()
    {
        return getStoredValue(_c, defaultNumberFormatString);
    }

    // Returns inherited value from the cache
    public String getExtraDateParsingPattern()
    {
        return FolderSettingsCache.getExtraDateParsingPattern(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getExtraDateParsingPattern() instead.
    public String calculateExtraDateParsingPattern()
    {
        // Look up this value starting from the current container
        return lookupStringValue(_c, extraDateParsingPattern, null);
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public String getExtraDateParsingPatternStored()
    {
        return getStoredValue(_c, extraDateParsingPattern);
    }

    // Returns inherited value from the cache
    public String getExtraDateTimeParsingPattern()
    {
        return FolderSettingsCache.getExtraDateTimeParsingPattern(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getExtraDateTimeParsingPattern() instead.
    public String calculateExtraDateTimeParsingPattern()
    {
        // Look up this value starting from the current container
        return lookupStringValue(_c, extraDateTimeParsingPattern, null);
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public String getExtraDateTimeParsingPatternStored()
    {
        return getStoredValue(_c, extraDateTimeParsingPattern);
    }

    // Returns inherited value from the cache
    public String getExtraTimeParsingPattern()
    {
        return FolderSettingsCache.getExtraTimeParsingPattern(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getExtraTimeParsingPattern() instead.
    public String calculateExtraTimeParsingPattern()
    {
        // Look up this value starting from the current container
        return lookupStringValue(_c, extraTimeParsingPattern, null);
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public String getExtraTimeParsingPatternStored()
    {
        return getStoredValue(_c, extraTimeParsingPattern);
    }

    // Returns inherited value from the cache
    public boolean areRestrictedColumnsEnabled()
    {
        return FolderSettingsCache.areRestrictedColumnsEnabled(_c);
    }

    // Note: Should be called only by FolderSettingsCache; other callers use getExtraTimeParsingPattern() instead.
    public boolean calculateRestrictedColumnsEnabled()
    {
        return lookupBooleanValue(_c, restrictedColumnsEnabled, false);
    }

    // Get the value that's actually stored in this container or null if inherited; don't look up the hierarchy.
    // This is useful for export and showing inheritance status in the UI.
    public Boolean areRestrictedColumnsEnabledStored()
    {
        String stored = getStoredValue(_c, restrictedColumnsEnabled);
        return null == stored ? null : "TRUE".equals(stored);
    }
}