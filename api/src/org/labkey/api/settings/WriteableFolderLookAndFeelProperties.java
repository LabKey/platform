/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import static org.labkey.api.settings.LookAndFeelFolderProperties.DEFAULT_DATE_FORMAT;
import static org.labkey.api.settings.LookAndFeelFolderProperties.DEFAULT_DATE_TIME_FORMAT;
import static org.labkey.api.settings.LookAndFeelFolderProperties.DEFAULT_NUMBER_FORMAT;
import static org.labkey.api.settings.LookAndFeelFolderProperties.RESTRICTED_COLUMNS_ENABLED;
import static org.labkey.api.settings.LookAndFeelProperties.LOOK_AND_FEEL_SET_NAME;

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

    protected String getType()
    {
        return "look and feel settings";
    }

    protected String getGroupName()
    {
        return LOOK_AND_FEEL_SET_NAME;
    }

    // Make public plus clear out the folder settings cache on every save
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
        storeStringValue(DEFAULT_DATE_FORMAT, defaultDateFormat);
    }

    // Validate inside the set method, since this is called from multiple places
    public void setDefaultDateTimeFormat(String defaultDateTimeFormat) throws IllegalArgumentException
    {
        // Check for legal format
        FastDateFormat.getInstance(defaultDateTimeFormat);
        storeStringValue(DEFAULT_DATE_TIME_FORMAT, defaultDateTimeFormat);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearDefaultDateFormat()
    {
        remove(DEFAULT_DATE_FORMAT);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearDefaultDateTimeFormat()
    {
        remove(DEFAULT_DATE_TIME_FORMAT);
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
        remove(DEFAULT_NUMBER_FORMAT);
    }

    // Validate inside the set method, since this is called from multiple places
    public void setDefaultNumberFormat(String defaultNumberFormat) throws IllegalArgumentException
    {
        new DecimalFormat(defaultNumberFormat);
        storeStringValue(DEFAULT_NUMBER_FORMAT, defaultNumberFormat);
    }

    // Convenience method to support import: validate and save just this property
    public static void saveDefaultNumberFormat(Container c, String defaultNumberFormat) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setDefaultNumberFormat(defaultNumberFormat);
        props.save();
    }

    public void clearRestrictedColumnsEnabled()
    {
        remove(RESTRICTED_COLUMNS_ENABLED);
    }

    public void setRestrictedColumnsEnabled(boolean restrictedColumnsEnabled) throws IllegalArgumentException
    {
        storeBooleanValue(RESTRICTED_COLUMNS_ENABLED, restrictedColumnsEnabled);
    }

    public static void saveRestrictedColumnsEnabled(Container c, boolean restrictedColumnsEnabled) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setRestrictedColumnsEnabled(restrictedColumnsEnabled);
        props.save();
    }
}
