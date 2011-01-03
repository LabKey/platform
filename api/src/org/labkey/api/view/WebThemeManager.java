/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.AppProps;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Oct 21, 2008
 * Time: 2:40:31 PM
 */
public class WebThemeManager
{
    private static final WebTheme BLUE  = new WebTheme("Blue", "000000", "003399", "BEBEBE", "FFFFFF", "FFFFFF", "003399", "EBF4FF");
    private static final WebTheme BROWN = new WebTheme("Brown", "000000", "003399", "B2B166","FFFFFF","FFFFFF", "003399", "B2B166");
    private static final WebTheme SEATTLE = new WebTheme("Seattle", "000000", "126495", "E7EFF4", "F8F8F8", "FFFFFF", "676767", "E0E6EA");
    
    // handle Web Theme color management
    private static final String THEME_NAMES_KEY = "themeNames";
    private static final String THEME_COLORS_KEY = "themeColors-";

    private final static Map<String, WebTheme> _webThemeMap = new TreeMap<String, WebTheme>();

    static
    {
        try
        {
            PropertyManager.PropertyMap properties = AppProps.getWebThemeConfigProperties();
            String themeNames = properties.get(THEME_NAMES_KEY);
            String[] themeNamesArray = (null == themeNames ? "" : themeNames).split(";");
            if (null != themeNamesArray)
            {
                // load the color settings from database
                for (String themeName : themeNamesArray)
                {
                    if (null == themeName || 0 == themeName.length())
                        continue;

                    // we read the colors separated by ';' in order
                    StringBuffer key = new StringBuffer();
                    key.append(THEME_COLORS_KEY);
                    key.append(themeName);
                    String themeColors = properties.get(key.toString());
                    String[] themeColorsArray = (null == themeColors ? "" : themeColors).split(";");
                    if (null != themeColorsArray || themeColorsArray.length > 0)
                    {
                        if (6 == themeColorsArray.length)
                        {
                            // Works for backwards compatibility to bootstrap old theme types
                            if (themeName.equals(BLUE.getFriendlyName()))
                            {
                                updateWebTheme(themeName, BLUE.getTextColor(), BLUE.getLinkColor(), BLUE.getGridColor(),
                                        BLUE.getPrimaryBackgroundColor(), BLUE.getSecondaryBackgroundColor(),
                                        BLUE.getBorderTitleColor(), BLUE.getWebPartColor());
                            }
                            else if (themeName.equals(BROWN.getFriendlyName()))
                            {
                                updateWebTheme(themeName, BROWN.getTextColor(), BROWN.getLinkColor(), BROWN.getGridColor(),
                                        BROWN.getPrimaryBackgroundColor(), BROWN.getSecondaryBackgroundColor(),
                                        BROWN.getBorderTitleColor(), BROWN.getWebPartColor());
                            }
                            else
                                continue;  // Old custom design themes do not get bootstrapped.
                        }
                        if (7 != themeColorsArray.length)
                        {
                            // incorrect number of colors defined, let's just skip it
                            continue;
                        }

                        String textColor = themeColorsArray[0];
                        String linkColor = themeColorsArray[1];
                        String gridColor = themeColorsArray[2];
                        String primaryBackgroundColor = themeColorsArray[3];
                        String secondaryBackgroundColor = themeColorsArray[4];
                        String borderTitleColor = themeColorsArray[5];
                        String webPartColor = themeColorsArray[6];

                        try
                        {
                            WebTheme webTheme = new WebTheme(
                                    themeName,
                                    textColor, linkColor,
                                    gridColor, primaryBackgroundColor,
                                    secondaryBackgroundColor, borderTitleColor, webPartColor);

                            addToMap(webTheme);
                        }
                        catch (IllegalArgumentException e)
                        {
                            // ignore color definition problem
                        }
                    }
                }
            }
        }
        catch (SQLException e)
        {
            // just continue
        }

        if (!_webThemeMap.containsKey(BLUE.getFriendlyName()))
        {
            addToMap(BLUE);
        }

        if (!_webThemeMap.containsKey(BROWN.getFriendlyName()))
        {
            addToMap(BROWN);
        }

        if (!_webThemeMap.containsKey(SEATTLE.getFriendlyName()))
        {
            addToMap(SEATTLE);  // no pun intended
        }
    }

    public final static WebTheme DEFAULT_THEME = SEATTLE;

    public static WebTheme getTheme(Container c)
    {
        Container settingsContainer = LookAndFeelProperties.getSettingsContainer(c);
        return lookupTheme(settingsContainer);
    }

    private static WebTheme lookupTheme(Container c)
    {
        WebTheme theme = null;

        try
        {
            LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);

            if (laf.hasProperties())
                theme = getTheme(laf.getThemeName());
        }
        catch (IllegalArgumentException e)
        {
        }

        if (null == theme)
        {
            if (c.isRoot())
               theme = DEFAULT_THEME;
            else
               theme = lookupTheme(c.getParent());   // Recurse up the chain
        }

        return theme;
    }

    public static Collection<WebTheme> getWebThemes()
    {
        synchronized(_webThemeMap)
        {
            // Only used by admin pages, so just give every caller a new copy of the values
            return new ArrayList<WebTheme>(_webThemeMap.values());
        }
    }

    public static WebTheme getTheme(String themeName)
    {
        if (null != themeName && 0 < themeName.length ())
        {
            synchronized(_webThemeMap)
            {
                return _webThemeMap.get(themeName);
            }
        }

        return null;
    }

    public static void updateWebTheme(String friendlyName, String textColor, String linkColor, String gridColor, String primaryBackgroundColor, String secondaryBackgroundColor, String borderTitleColor, String webPartColor)
        throws SQLException
    {
        WebTheme updateTheme = new WebTheme(friendlyName, textColor, linkColor, gridColor, primaryBackgroundColor, secondaryBackgroundColor, borderTitleColor, webPartColor);

        synchronized(_webThemeMap)
        {
            removeFromMap(friendlyName);
            addToMap(updateTheme);
            saveWebThemes();
        }
    }

    public static void deleteWebTheme(String friendlyName) throws SQLException
    {
        synchronized(_webThemeMap)
        {
            removeFromMap(friendlyName);
            saveWebThemes();
        }
    }

    // Synchronized externally
    private static void addToMap(WebTheme theme)
    {
        _webThemeMap.put(theme.getFriendlyName(), theme);
    }

    // Synchronized externally
    private static void removeFromMap(String friendlyName)
    {
        if (null == friendlyName || 0 == friendlyName.length())
            return;

        _webThemeMap.remove(friendlyName);
    }

    private static void saveWebThemes() throws SQLException
    {
        synchronized(_webThemeMap)
        {
            PropertyManager.PropertyMap properties = AppProps.getWebThemeConfigProperties();

            // save all the theme names
            properties.put(THEME_NAMES_KEY, StringUtils.join(_webThemeMap.values(), ';'));

            // save all the definitions
            for (WebTheme theme : _webThemeMap.values())
            {
                StringBuilder key = new StringBuilder();
                key.append(THEME_COLORS_KEY);
                key.append(theme.getFriendlyName());
                StringBuilder def = new StringBuilder();
                def.append(theme.getTextColor());
                def.append(";").append(theme.getLinkColor());
                def.append(";").append(theme.getGridColor());
                def.append(";").append(theme.getPrimaryBackgroundColor());
                def.append(";").append(theme.getSecondaryBackgroundColor());
                def.append(";").append(theme.getBorderTitleColor());
                def.append(";").append(theme.getWebPartColor());
                properties.put(key.toString(), def.toString());
            }

            PropertyManager.saveProperties(properties);
        }
    }
}
