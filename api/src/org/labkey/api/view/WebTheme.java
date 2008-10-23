/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

    WebTheme(String friendlyName, String navBarColor, String headerLineColor, String editFormColor, String fullScreenBorderColor, String titleBarBackgroundColor, String titleBarBorderColor)
    {
        _friendlyName = friendlyName;

        _navBarColor = navBarColor;
        _headerLineColor = headerLineColor;
        _editFormColor = editFormColor;
        _fullScreenBorderColor = fullScreenBorderColor;
        _titleBarBackgroundColor = parseColor(titleBarBackgroundColor);
        _titleBarBorderColor = parseColor(titleBarBorderColor);
        // UNDONE: save restore this color
        _titleColor = new Color(0x003399);

        // perform testing on the colour code
        Color _testColor;
        _testColor = parseColor(navBarColor);
        _testColor = parseColor(headerLineColor);
        _testColor = parseColor(editFormColor);
        _testColor = parseColor(fullScreenBorderColor);
    }

    private Color parseColor(String s)
    {
        if (s.length() != 6)
        {
            throw new IllegalArgumentException("Colors must be 6 hex digits, but was " + s);
        }
        int r = Integer.parseInt(s.substring(0, 2), 16);
        int g = Integer.parseInt(s.substring(2, 4), 16);
        int b = Integer.parseInt(s.substring(4, 6), 16);
        return new Color(r, g, b);
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
/*        StringBuffer buffer = new StringBuffer(6);

        String red = Integer.toHexString(c.getRed());
        String green = Integer.toHexString(c.getGreen());
        String blue = Integer.toHexString(c.getBlue());

        if (red.length() == 1)
            buffer.append("0");
        buffer.append(red.toUpperCase());

        if (green.length() == 1)
            buffer.append("0");
        buffer.append(green.toUpperCase());

        if (blue.length() == 1)
            buffer.append("0");
        buffer.append(blue.toUpperCase());

        return buffer.toString (); */
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

