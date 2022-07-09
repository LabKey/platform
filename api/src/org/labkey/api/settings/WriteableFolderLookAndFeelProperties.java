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

import org.apache.commons.lang3.time.FastDateFormat;
import org.labkey.api.data.Container;

import java.text.DecimalFormat;

import static org.labkey.api.settings.LookAndFeelProperties.LOOK_AND_FEEL_SET_NAME;

import static org.labkey.api.settings.LookAndFeelFolderProperties.defaultDateFormatString;
import static org.labkey.api.settings.LookAndFeelFolderProperties.defaultDateTimeFormatString;
import static org.labkey.api.settings.LookAndFeelFolderProperties.defaultNumberFormatString;

import static org.labkey.api.settings.LookAndFeelProperties.Properties.*;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */

// Handles only the properties that can be set at the folder level
public class WriteableFolderLookAndFeelProperties extends AbstractWriteableSettingsGroup
{
    WriteableFolderLookAndFeelProperties(Container c)
    {
        makeWriteable(c);
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

    // Make public plus clear out the folder settings cache on every save
    @Override
    public void save()
    {
        super.save();
        FolderSettingsCache.clear();
    }

    public void clear(boolean hasAdminOpsPerm)
    {
        getProperties().clear();
    }

    // Validate inside the set method, since this is called from multiple places
    public void setDefaultDateFormat(String defaultDateFormat) throws IllegalArgumentException
    {
        // Check for legal format
        FastDateFormat.getInstance(defaultDateFormat);
        storeStringValue(defaultDateFormatString, defaultDateFormat);
    }

    // Validate inside the set method, since this is called from multiple places
    public void setDefaultDateTimeFormat(String defaultDateTimeFormat) throws IllegalArgumentException
    {
        // Check for legal format
        FastDateFormat.getInstance(defaultDateTimeFormat);
        storeStringValue(defaultDateTimeFormatString, defaultDateTimeFormat);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearDefaultDateFormat()
    {
        remove(defaultDateFormatString);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearDefaultDateTimeFormat()
    {
        remove(defaultDateTimeFormatString);
    }

    // Convenience method to support import: validate and save just this property
    public static void saveDefaultDateFormat(Container c, String defaultDateFormat) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setDefaultDateFormat(defaultDateFormat);
        props.save();
    }

    // Convenience method to support import: validate and save just this property
    public static void saveDefaultDateTimeFormat(Container c, String defaultDateTimeFormat) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setDefaultDateTimeFormat(defaultDateTimeFormat);
        props.save();
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearDefaultNumberFormat()
    {
        remove(defaultNumberFormatString);
    }

    // Validate inside the set method, since this is called from multiple places
    public void setDefaultNumberFormat(String defaultNumberFormat) throws IllegalArgumentException
    {
        new DecimalFormat(defaultNumberFormat);
        storeStringValue(defaultNumberFormatString, defaultNumberFormat);
    }

    // Convenience method to support import: validate and save just this property
    public static void saveDefaultNumberFormat(Container c, String defaultNumberFormat) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setDefaultNumberFormat(defaultNumberFormat);
        props.save();
    }

    // Validate inside the set method, since this is called from multiple places
    public void setExtraDateParsingPattern(String pattern) throws IllegalArgumentException
    {
        // Check for legal format
        FastDateFormat.getInstance(pattern);
        storeStringValue(extraDateParsingPattern, pattern);
    }

    // Validate inside the set method, since this is called from multiple places
    public void setExtraDateTimeParsingPattern(String pattern) throws IllegalArgumentException
    {
        // Check for legal format
        FastDateFormat.getInstance(pattern);
        storeStringValue(extraDateTimeParsingPattern, pattern);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearExtraDateParsingPattern()
    {
        remove(extraDateParsingPattern);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearExtraDateTimeParsingPattern()
    {
        remove(extraDateTimeParsingPattern);
    }

    // Convenience method to support import: validate and save just this property
    public static void saveExtraDateParsingPattern(Container c, String extraDateParsingPattern) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setExtraDateParsingPattern(extraDateParsingPattern);
        props.save();
    }

    // Convenience method to support import: validate and save just this property
    public static void saveExtraDateTimeParsingPattern(Container c, String extraDateTimeParsingPattern) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setDefaultDateTimeFormat(extraDateTimeParsingPattern);
        props.save();
    }

    public void clearRestrictedColumnsEnabled()
    {
        remove(restrictedColumnsEnabled);
    }

    public void setRestrictedColumnsEnabled(boolean enabled) throws IllegalArgumentException
    {
        storeBooleanValue(restrictedColumnsEnabled, enabled);
    }

    public static void saveRestrictedColumnsEnabled(Container c, boolean restrictedColumnsEnabled) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setRestrictedColumnsEnabled(restrictedColumnsEnabled);
        props.save();
    }
}
