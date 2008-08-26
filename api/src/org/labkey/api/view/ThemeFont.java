/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.settings.LookAndFeelProperties;

import java.util.ArrayList;
import java.util.List;

public class ThemeFont
{
    //protected final static ThemeFont VERYSMALL = new ThemeFont("Very small", "7pt", "13pt", "10pt", "18pt", "9pt", "9pt", "10", "11");
    protected final static ThemeFont SMALL = new ThemeFont("Small", "8pt", "10pt", "14pt", "11pt", "19pt", "10pt", "10pt", "11", "12");
    protected final static ThemeFont MEDIUM = new ThemeFont("Medium", "10pt", "10pt", "16pt", "13pt", "21pt", "12pt", "12pt", "13", "14");
    protected final static ThemeFont LARGE = new ThemeFont("Large", "12pt", "12pt", "18pt", "15pt", "23pt", "14pt", "14pt", "15", "16");

    private final String _friendlyName;
    private final String _sizeNormal;
    private final String _sizeTextInput;
    private final String _sizePageTitle;
    private final String _sizePageHeader;
    private final String _sizeButtonHeight;
    private final String _sizeHeading_1;
    private final String _sizeHeading_1_1;
    private final String _sizeGraphicButtonTextHeight;
    private final String _sizeGraphicTabTextHeight;

    private ThemeFont(String friendlyName, String sizeNormal, String sizeTextInput, String sizePageTitle, String sizePageHeader, String sizeButtonHeight,
        String sizeHeading_1, String sizeHeading_1_1, String sizeGraphicButtonTextHeight, String sizeGraphicTabTextHeight)
    {
        _friendlyName = friendlyName;

        _sizeNormal = sizeNormal;
        _sizeTextInput = sizeTextInput;
        _sizePageTitle = sizePageTitle;
        _sizePageHeader = sizePageHeader;
        _sizeButtonHeight = sizeButtonHeight;
        _sizeHeading_1 = sizeHeading_1;
        _sizeHeading_1_1 = sizeHeading_1_1;
        _sizeGraphicButtonTextHeight = sizeGraphicButtonTextHeight;
        _sizeGraphicTabTextHeight = sizeGraphicTabTextHeight;
    }

    public String getNormalSize()
    {
        return _sizeNormal;
    }

    public String getTextInputSize()
    {
        return _sizeTextInput;
    }

    public String getPageTitleSize()
    {
        return _sizePageTitle;
    }

    public String getPageHeaderSize()
    {
        return _sizePageHeader;
    }

    public String getButtonHeight()
    {
        return _sizeButtonHeight;
    }

    public String getHeader_1Size()
    {
        return _sizeHeading_1;
    }

    public String getHeader_1_1Size()
    {
        return _sizeHeading_1_1;
    }

    public String getGraphicButtonTextHeight()
    {
        return _sizeGraphicButtonTextHeight;
    }

    public String getGraphicTabTextHeight()
    {
        return _sizeGraphicTabTextHeight;
    }

    public String getFriendlyName()
    {
        return _friendlyName;
    }

    public String toString()
    {
        return _friendlyName;
    }

    public String getId()
    {
        return  StringUtils.replace(_friendlyName, " ", "-");
    }

    public final static ThemeFont DEFAULT_THEME_FONT = SMALL;
    protected static List<ThemeFont> webThemeFontList = new ArrayList<ThemeFont>();

    @Deprecated
    public static ThemeFont getThemeFont()
    {
        return getThemeFont(ContainerManager.getRoot());
    }

    public static ThemeFont getThemeFont(Container c)
    {
        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
        return ThemeFont.getThemeFont(laf.getThemeFont());
    }

    public static ThemeFont getThemeFont(String themeFont)
    {
        if (null != themeFont && 0 < themeFont.length ())
        {
            // locate the name
            for (ThemeFont webThemeFont : ThemeFont.getThemeFonts())
            {
                if (webThemeFont.getFriendlyName().equals(themeFont))
                {
                    return webThemeFont;
                }
            }
        }
        return null;
    }

    public static List<ThemeFont> getThemeFonts()
    {
        if (0 == webThemeFontList.size())
        {
//            webThemeFontList.add (VERYSMALL);
            webThemeFontList.add (SMALL);
            webThemeFontList.add (MEDIUM);
            webThemeFontList.add (LARGE);
        }

        return webThemeFontList;
    }
}
