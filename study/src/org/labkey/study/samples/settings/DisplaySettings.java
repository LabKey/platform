/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.samples.settings;

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
    private DisplayOption _lastVial;
    private DisplayOption _zeroVials;
    public static enum DisplayOption
    {
        NONE,
        ALL_USERS,
        ADMINS_ONLY
    }

    public DisplaySettings()
    {
        // no-arg constructor for struts reflection
    }

    public DisplaySettings(Map<String, String> map)
    {
        _lastVial = DisplayOption.valueOf(map.get(KEY_FLAG_ONE_AVAIL_VIAL));
        _zeroVials = DisplayOption.valueOf(map.get(KEY_FLAG_ZERO_AVAIL_VIALS));
    }

    public void populateMap(Map<String, String> map)
    {
        map.put(KEY_FLAG_ONE_AVAIL_VIAL, _lastVial.name());
        map.put(KEY_FLAG_ZERO_AVAIL_VIALS, _zeroVials.name());
    }

    public static DisplaySettings getDefaultSettings()
    {
        DisplaySettings defaults = new DisplaySettings();
        defaults.setLastVial(DisplayOption.ALL_USERS.name());
        defaults.setZeroVials(DisplayOption.ALL_USERS.name());
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
}