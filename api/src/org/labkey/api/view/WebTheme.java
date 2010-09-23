/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
package org.labkey.api.view;

import java.awt.*;

/**
 * User: jeckels
 * Date: Oct 11, 2005
 */
public class WebTheme
{
    private final String _navBarColor;
    private final String _headerLineColor;
    private final String _editFormColor;
    private final String _friendlyName;
    private final String _fullScreenBorderColor;
    private final Color _titleBarBackgroundColor;
    private final Color _titleBarBorderColor;
    private final Color _titleColor;
    private final boolean _custom;
    private final String _stylesheet;
    
    WebTheme(String friendlyName, String StylesheetName)
    {
        if (null == friendlyName)
            throw new IllegalArgumentException("You must specify a name for this theme");
        else if (null == StylesheetName)
            throw new IllegalArgumentException("You must specify a stylesheet for the Web Theme ->" + friendlyName);
        _friendlyName = friendlyName;

        // This is a custom theme, defined by the stylesheet given as a parameter -- only set during init
        _custom = true;
        _stylesheet = StylesheetName;
        
        // This theme does not use any WebTheme color definitions;
        _navBarColor = null;
        _headerLineColor = null;
        _editFormColor = null;
        _fullScreenBorderColor = null;
        _titleBarBackgroundColor = null;
        _titleBarBorderColor = null;
        _titleColor = null;
    }

    WebTheme(String friendlyName, String navBarColor, String headerLineColor, String editFormColor, String fullScreenBorderColor, String titleBarBackgroundColor, String titleBarBorderColor)
    {
        if (null == friendlyName)
            throw new IllegalArgumentException("You must specify a name for this theme");

        // perform testing on the color code
        parseColor(navBarColor);
        parseColor(headerLineColor);
        parseColor(editFormColor);
        parseColor(fullScreenBorderColor);

        _friendlyName = friendlyName;
        _navBarColor = navBarColor;
        _headerLineColor = headerLineColor;
        _editFormColor = editFormColor;
        _fullScreenBorderColor = fullScreenBorderColor;
        _titleBarBackgroundColor = parseColor(titleBarBackgroundColor);
        _titleBarBorderColor = parseColor(titleBarBorderColor);
        // UNDONE: save restore this color
        _titleColor = new Color(0x003399);
        
        _custom = false;
        _stylesheet = "stylesheet.css";
    }

    private Color parseColor(String s)
    {
        if (null == s)
        {
            throw new IllegalArgumentException("You must specify a value for every color");
        }
        if (s.length() != 6)
        {
            throw new IllegalArgumentException("Colors must be 6 hex digits, but was " + s);
        }
        int r = Integer.parseInt(s.substring(0, 2), 16);
        int g = Integer.parseInt(s.substring(2, 4), 16);
        int b = Integer.parseInt(s.substring(4, 6), 16);
        return new Color(r, g, b);
    }

    public boolean isCustom()
    {
        return _custom;
    }

    public String getStyleSheet()
    {
        return _stylesheet;
    }
    
    public String getNavBarColor()
    {
        return _navBarColor;
    }

    public String getHeaderLineColor()
    {
        return _headerLineColor;
    }

    public String getEditFormColor()
    {
        return _editFormColor;
    }

    public String getFriendlyName()
    {
        return _friendlyName;
    }

    public String getFullScreenBorderColor()
    {
        return _fullScreenBorderColor;
    }

    public String toString()
    {
        return _friendlyName;
    }

    private static String toRGB(Color c)
    {
        String rgb = Integer.toHexString(0x00ffffff & c.getRGB());
        return rgb.length() == 6 ? rgb : "000000".substring(rgb.length()) + rgb;
    }

    public String getTitleBarBackgroundString()
    {
        return toRGB(_titleBarBackgroundColor);
    }

    public String getTitleBarBorderString()
    {
        return toRGB(_titleBarBorderColor);
    }

    public Color getTitleColor()
    {
        return _titleColor;
    }

    public String getTitleColorString()
    {
        return toRGB(_titleColor);
    }

}

