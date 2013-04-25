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
    @Deprecated
    OPTIONAL_ON("Optionally -- Show by default", true),
    @Deprecated
    OPTIONAL_OFF("Optionally -- Hide by default", true),
    @Deprecated
    IN_MENU("As drop down menu", true),
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
    
    public static FolderDisplayMode fromString(String str)
    {
        return null == StringUtils.trimToNull(str) ? ALWAYS : valueOf(str);         
    }

    public boolean isShowInMenu()
    {
        return showInMenu;
    }
}
