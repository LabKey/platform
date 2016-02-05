/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ScalabilityProblem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manages the available {@link WebTheme} instances. Includes both built-in options and admin-created themes.
 * User: adam
 * Date: Oct 21, 2008
 */
public class WebThemeManager
{
    private static final WebTheme BLUE  =   new WebTheme("Blue",    "212121", "21309A", "E4E6EA", "F4F4F4", "FFFFFF", "3441A1", "D0DBEE", false);
    private static final WebTheme BROWN =   new WebTheme("Brown",   "212121", "682B16", "EBE2DB", "F4F4F4", "FFFFFF", "682B16", "DFDDD9", false);
    private static final WebTheme HARVEST = new WebTheme("Harvest", "212121", "892405", "F5E2BB", "F4F4F4", "FFFFFF", "892405", "DBD8D2", false);
    private static final WebTheme SAGE  =   new WebTheme("Sage",    "212121", "0F4F0B", "D4E4D3", "F4F4F4", "FFFFFF", "386135", "E1E5E1", false);
    private static final WebTheme MADISON = new WebTheme("Madison", "212121", "990000", "FFECB0", "FFFCF8", "FFFFFF", "CCCCCC", "EEEBE0", false);
    // NOTE: DEFAULT theme (SEATTLE) is defined in WebTheme, since we need it in cases where we can't initialize this class (e.g., database not supported exception)

    // handle Web Theme color management
    private static final String THEME_NAMES_KEY = "themeNames";
    private static final String THEME_COLORS_KEY = "themeColors-";

    @ScalabilityProblem   // Should use a proper cache
    private static final Map<String, WebTheme> _webThemeMap = new TreeMap<>();

    static
    {
        PropertyMap properties = AppProps.getWebThemeConfigProperties();
        String themeNames = properties.get(THEME_NAMES_KEY);
        String[] themeNamesArray = (null == themeNames ? "" : themeNames).split(";");

        addToMap(BLUE);
        addToMap(BROWN);
        addToMap(HARVEST);
        addToMap(SAGE);
        addToMap(MADISON);
        addToMap(WebTheme.DEFAULT);

        // load the color settings from database
        for (String themeName : themeNamesArray)
        {
            if (null == themeName || 0 == themeName.length())
                continue;

            // we read the colors separated by ';' in order
            String key = THEME_COLORS_KEY + themeName;
            String themeColors = properties.get(key);
            String[] themeColorsArray = (null == themeColors ? "" : themeColors).split(";");
            if (themeColorsArray.length > 0)
            {
                // Load default themes
                if (_webThemeMap.containsKey(themeName))
                {
                    updateWebTheme(getTheme(themeName));
                }

                // Load user defined themes
                else if (7 == themeColorsArray.length)
                {
                    // Assumes correct order
                    String textColor = themeColorsArray[0];
                    String linkColor = themeColorsArray[1];
                    String gridColor = themeColorsArray[2];
                    String primaryBackgroundColor = themeColorsArray[3];
                    String secondaryBackgroundColor = themeColorsArray[4];
                    String borderTitleColor = themeColorsArray[5];
                    String webPartColor = themeColorsArray[6];

                    WebTheme webTheme = new WebTheme(
                            themeName,
                            textColor, linkColor,
                            gridColor, primaryBackgroundColor,
                            secondaryBackgroundColor, borderTitleColor, webPartColor, true);
                    updateWebTheme(webTheme);
                }
            }
        }
    }

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
               theme = WebTheme.DEFAULT;
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
            return new ArrayList<>(_webThemeMap.values());
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

    public static void updateWebTheme(WebTheme theme)
    {
        synchronized(_webThemeMap)
        {
            removeFromMap(theme.getFriendlyName());
            addToMap(theme);
            saveWebThemes();
        }
    }
    
    public static void updateWebTheme(String friendlyName, String textColor, String linkColor, String gridColor, String primaryBackgroundColor, String secondaryBackgroundColor, String borderTitleColor, String webPartColor)
    {
        updateWebTheme(new WebTheme(friendlyName, textColor, linkColor, gridColor, primaryBackgroundColor, secondaryBackgroundColor, borderTitleColor, webPartColor));
    }

    public static void deleteWebTheme(String friendlyName)
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

    private static void saveWebThemes()
    {
        synchronized(_webThemeMap)
        {
            PropertyMap properties = AppProps.getWebThemeConfigProperties();

            // save all the theme names
            properties.put(THEME_NAMES_KEY, StringUtils.join(_webThemeMap.values(), ';'));

            // save all the definitions
            for (WebTheme theme : _webThemeMap.values())
            {
                String key = THEME_COLORS_KEY + theme.getFriendlyName();
                String def = theme.getTextColor() +
                        ";" + theme.getLinkColor() +
                        ";" + theme.getGridColor() +
                        ";" + theme.getPrimaryBackgroundColor() +
                        ";" + theme.getSecondaryBackgroundColor() +
                        ";" + theme.getBorderTitleColor() +
                        ";" + theme.getWebPartColor();
                properties.put(key, def);
            }

            properties.save();
        }
    }
}
