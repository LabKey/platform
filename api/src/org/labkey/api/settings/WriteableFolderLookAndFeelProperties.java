/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.labkey.api.util.DateUtil;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.labkey.api.settings.LookAndFeelFolderProperties.DEFAULT_DATE_FORMAT;
import static org.labkey.api.settings.LookAndFeelFolderProperties.DEFAULT_NUMBER_FORMAT;
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

    public void clear()
    {
        getProperties().clear();
    }

    // Validate inside the set method, since this is called from multiple places
    public void setDefaultDateFormat(String defaultDateFormat) throws IllegalArgumentException
    {
        // Check for legal format
        FastDateFormat.getInstance(defaultDateFormat);

        // Now verify that we can parse dates in this format, since we will use this format in input forms
        // TODO: switch DateUtil.parseDate() to use the custom format for parsing, which requires all usages to push
        // a container into parseDate()
        SimpleDateFormat format = new SimpleDateFormat(defaultDateFormat);
        Calendar cal = new GregorianCalendar();
        cal.clear();
        cal.set(2013, Calendar.DECEMBER, 31);  // 12/31/2013
        Date testDate = cal.getTime();
        try
        {
            Date testDate2 = new Date(DateUtil.parseDate(format.format(testDate)));
            if (!testDate.equals(testDate2))
                throw new IllegalArgumentException("Can't parse using date format \"" + defaultDateFormat + "\": " + testDate + " does not match " + testDate2);

        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Can't parse using date format \"" + defaultDateFormat + "\"", e);
        }
        storeStringValue(DEFAULT_DATE_FORMAT, defaultDateFormat);
    }

    // Allows clearing the property to allow inheriting of this property alone. Should make this more obvious and universal, via "inherit/override" checkboxes and highlighting in the UI
    public void clearDefaultDateFormat()
    {
        remove(DEFAULT_DATE_FORMAT);
    }

    // Convenience method to support import: validate and save just this property
    public static void saveDefaultDateFormat(Container c, String defaultDateFormat) throws IllegalArgumentException
    {
        WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);
        props.setDefaultDateFormat(defaultDateFormat);
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
}
