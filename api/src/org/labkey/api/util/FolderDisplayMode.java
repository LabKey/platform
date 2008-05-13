/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 3, 2007
 * Time: 12:49:57 PM
 */
public enum FolderDisplayMode
{
    ALWAYS("Always"),
    OPTIONAL_ON("Optionally -- Show by default"),
    OPTIONAL_OFF("Optionally -- Hide by default"),
    ADMIN("Only in admin mode");

    private String displayString;
    FolderDisplayMode(String displayString)
    {
        this.displayString = displayString;
    }

    public String getDisplayString()
    {
        return displayString;
    }
    
    public static FolderDisplayMode fromString(String str)
    {
        return null == StringUtils.trimToNull(str) ? ALWAYS : valueOf(str);         
    }
}
