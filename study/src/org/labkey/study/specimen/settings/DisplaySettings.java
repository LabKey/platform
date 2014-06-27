/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.study.specimen.settings;

import java.util.Map;
/*
 * User: brittp
 * Date: May 8, 2009
 * Time: 2:51:21 PM
 */

public class DisplaySettings
{
    private static final String KEY_FLAG_ONE_AVAIL_VIAL = "OneAvailableVial";
    private static final String KEY_FLAG_ZERO_AVAIL_VIALS = "ZeroAvailableVials";

    private static final String KEY_DEFAULT_COMMENT_MODE = "DefaultToCommentMode";
    private static final String KEY_ENABLE_MANUAL_QC_FLAGGING = "EnableManualQCFlagging";

    private DisplayOption _lastVial;
    private DisplayOption _zeroVials;
    public static enum DisplayOption
    {
        NONE,
        ALL_USERS,
        ADMINS_ONLY
    }
    private boolean _defaultToCommentsMode = false;
    private boolean _enableManualQCFlagging = true;

    public DisplaySettings()
    {
        // no-arg constructor for BeanViewForm reflection
    }

    public DisplaySettings(Map<String, String> map)
    {
        // Defect 15165: check for NULL properties; (this case seems to be _lastVial was present but not _zeroVials)
        String propertyValue = map.get(KEY_FLAG_ONE_AVAIL_VIAL);
        _lastVial = (propertyValue != null) ? DisplayOption.valueOf(propertyValue) : DisplayOption.ALL_USERS;
        propertyValue = map.get(KEY_FLAG_ZERO_AVAIL_VIALS);
        _zeroVials = (propertyValue != null) ? DisplayOption.valueOf(propertyValue) : DisplayOption.ALL_USERS;
        _defaultToCommentsMode = getBoolean(map.get(KEY_DEFAULT_COMMENT_MODE), false);
        _enableManualQCFlagging = getBoolean(map.get(KEY_ENABLE_MANUAL_QC_FLAGGING), true);
    }

    private boolean getBoolean(Object value, boolean defaultValue)
    {
        if (value == null)
            return defaultValue;
        if (value instanceof String)
            return Boolean.valueOf((String) value).booleanValue();
        throw new IllegalArgumentException("Cannot convert object to boolean: " + value);
    }

    public void populateMap(Map<String, String> map)
    {
        map.put(KEY_FLAG_ONE_AVAIL_VIAL, _lastVial.name());
        map.put(KEY_FLAG_ZERO_AVAIL_VIALS, _zeroVials.name());
        map.put(KEY_DEFAULT_COMMENT_MODE, Boolean.toString(_defaultToCommentsMode));
        map.put(KEY_ENABLE_MANUAL_QC_FLAGGING, Boolean.toString(_enableManualQCFlagging));
    }

    public static DisplaySettings getDefaultSettings()
    {
        DisplaySettings defaults = new DisplaySettings();
        defaults.setLastVial(DisplayOption.ALL_USERS.name());
        defaults.setZeroVials(DisplayOption.ALL_USERS.name());
        defaults.setDefaultToCommentsMode(false);
        defaults.setEnableManualQCFlagging(true);
        return defaults;
    }

    public String getLastVial()
    {
        return _lastVial.name();
    }

    public void setLastVial(String lastVial)
    {
        _lastVial = DisplayOption.valueOf(lastVial);
    }

    public String getZeroVials()
    {
        return _zeroVials.name();
    }

    public void setZeroVials(String zeroVials)
    {
        _zeroVials = DisplayOption.valueOf(zeroVials);
    }

    public DisplayOption getLastVialEnum()
    {
        return _lastVial;
    }

    public DisplayOption getZeroVialsEnum()
    {
        return _zeroVials;
    }

    public boolean isDefaultToCommentsMode()
    {
        return _defaultToCommentsMode;
    }

    public void setDefaultToCommentsMode(boolean defaultToCommentsMode)
    {
        _defaultToCommentsMode = defaultToCommentsMode;
    }

    public boolean isEnableManualQCFlagging()
    {
        return _enableManualQCFlagging;
    }

    public void setEnableManualQCFlagging(boolean enableManualQCFlagging)
    {
        _enableManualQCFlagging = enableManualQCFlagging;
    }
}