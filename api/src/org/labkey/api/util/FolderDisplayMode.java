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
