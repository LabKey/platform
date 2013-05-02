/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 3, 2007
 * Time: 12:49:57 PM
 */
public enum FolderDisplayMode
{
    ALWAYS("Always", false),
    ADMIN("Only for Administrators", false);

    private String displayString;
    private boolean showInMenu;
    FolderDisplayMode(String displayString, boolean showInMenu)
    {
        this.displayString = displayString;
        this.showInMenu = showInMenu;
    }

    public String getDisplayString()
    {
        return displayString;
    }

    /**
     * Will return FolderDisplayMode.ALWAYS for any non legal enum value.
     * @param str String value of the the enumeration
     * @return FolderDisplayMode
     */
    public static FolderDisplayMode fromString(String str)
    {
        FolderDisplayMode mode = ALWAYS; // default

        if (null == StringUtils.trimToNull(str))
            return mode;

        try
        {
            mode = valueOf(str);
        }
        catch (IllegalArgumentException e)
        {
            /* Skip setting */
        }
        return mode;
    }

    public boolean isShowInMenu()
    {
        return showInMenu;
    }
}
