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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelAppProps;
import org.labkey.api.settings.WriteableLookAndFeelAppProps;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: jeckels
 * Date: Oct 11, 2005
 */
public class WebTheme
{
    protected final static WebTheme BLUE = new WebTheme("Blue", "e1ecfc", "89a1b4", "ffdf8c", "336699", "ebf4ff", "89a1b4");
    protected final static WebTheme BROWN = new WebTheme("Brown", "cccc99", "929146", "e1e1c4", "666633", "e1e1c4", "929146");

    private final String _navBarColor;
    private final String _headerLineColor;
    private final String _editFormColor;
    private final String _friendlyName;
    private final String _fullScreenBorderColor;
    private final Color _gradientLightColor;
    private final Color _gradientDarkColor;
    private final Color _titleColor;

    private WebTheme(String friendlyName, String navBarColor, String headerLineColor, String editFormColor, String fullScreenBorderColor, String gradientLightColor, String gradientDarkColor)
    {
        _friendlyName = friendlyName;

        _navBarColor = navBarColor;
        _headerLineColor = headerLineColor;
        _editFormColor = editFormColor;
        _fullScreenBorderColor = fullScreenBorderColor;
        _gradientLightColor = parseColor(gradientLightColor);
        _gradientDarkColor = parseColor(gradientDarkColor);
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

    public static String toRGB(Color c)
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


    public String getGradientLightString()
    {
        return toRGB(_gradientLightColor);
    }

    public String getGradientDarkString()
    {
        return toRGB(_gradientDarkColor);
    }

    private static Map<Container, WebTheme> _themeCache = new ConcurrentHashMap<Container, WebTheme>();
    private static WebTheme _theme = null;
    public final static WebTheme DEFAULT_THEME = BLUE;

    public static void setTheme(Container c, WebTheme theme) throws SQLException
    {
        _themeCache.put(c, theme);
    }

    @Deprecated
    public static WebTheme getTheme()
    {
        return getTheme(ContainerManager.getRoot());
    }

    public static WebTheme getTheme(Container c)
    {
        Container settingsContainer = LookAndFeelAppProps.getSettingsContainer(c);
        return lookupTheme(settingsContainer);
    }

    private static WebTheme lookupTheme(Container c)
    {
        WebTheme theme = _themeCache.get(c);

        if (null != theme)
            return theme;

        try
        {
            LookAndFeelAppProps laf = LookAndFeelAppProps.getInstance(c);

            if (laf.hasProperties())
                theme = WebTheme.getTheme(laf.getThemeName());
        }
        catch (IllegalArgumentException e)
        {
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (null == theme)
        {
            if (c.isRoot())
               theme = DEFAULT_THEME;
            else
               theme = lookupTheme(c.getParent());   // Recurse up the chain
        }

        _themeCache.put(c, theme);
        return theme;
    }

    // handle Web Theme color management
    protected static final String THEME_NAMES_KEY = "themeNames";
    protected static final String THEME_COLORS_KEY = "themeColors-";
    protected static List<WebTheme> _webThemeList = new ArrayList<WebTheme>();

    public static List<WebTheme> getWebThemes()
    {
        if (0 == _webThemeList.size())
        {
            boolean loadDefault = true;

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

                        // let's get the colours
                        // we read the colors separated by ';' in order
                        StringBuffer key = new StringBuffer();
                        key.append(THEME_COLORS_KEY);
                        key.append(themeName);
                        String themeColours = properties.get(key.toString());
                        String[] themeColoursArray = (null == themeColours ? "" : themeColours).split(";");
                        if (null == themeColoursArray || 0 == themeColoursArray.length)
                        {
                            // no colour defined, let's just skip it
                            continue;
                        }
                        else
                        {
                            if (6 != themeColoursArray.length)
                            {
                                // incorrect number of colours defined, let's just skip it
                                continue;
                            }

                            String navBarColor = themeColoursArray[0];
                            String headerLineColor = themeColoursArray[1];
                            String editFormColor = themeColoursArray[2];
                            String fullScreenBorderColor = themeColoursArray[3];
                            String gradientLightColor = themeColoursArray[4];
                            String gradientDarkColor = themeColoursArray[5];

                            try
                            {
                                WebTheme webTheme = new WebTheme(
                                        themeName,
                                        navBarColor, headerLineColor,
                                        editFormColor, fullScreenBorderColor,
                                        gradientLightColor, gradientDarkColor);

                                _webThemeList.add(webTheme);
                            }
                            catch (IllegalArgumentException e)
                            {
                                // let's just ignore colour definition problem
                            }
                        }
                    }
                }

                if (_webThemeList.size() > 0)
                {
                    // some colour loaded
                    loadDefault = false;
                }
            }
            catch (SQLException e)
            {
                // load the default as we need to continue
            }

            if (loadDefault)
            {
                // load the default as we need to continue
                _webThemeList.add(BLUE);
                _webThemeList.add(BROWN);
            }
        }
        return _webThemeList;
    }

    public static WebTheme getTheme(String themeName)
    {
        if (null != themeName && 0 < themeName.length ())
        {
            // locate the name
            for (WebTheme theme : WebTheme.getWebThemes())
            {
                if (theme.getFriendlyName().equals(themeName))
                {
                    return theme;
                }
            }
        }
        return null;
    }

    private static void updateWebTheme(WebTheme webTheme)
    {
        if (null == webTheme) return;
        String webThemeName = webTheme.getFriendlyName();
        if (null != webThemeName && 0 < webThemeName.length())
        {
            List<WebTheme> webThemeList = WebTheme.getWebThemes();
            boolean themeFound = false;
            // locate the name
            for (int i = 0; i < webThemeList.size(); i++)
            {
                WebTheme theme = webThemeList.get(i);
                if (theme.getFriendlyName().equals(webThemeName))
                {
                    themeFound = true;
                    webThemeList.set(i, webTheme);
                    if (_theme == theme)
                    {
                        _theme = webTheme;
                    }
                    break;
                }
            }
            if (!themeFound)
            {
                _webThemeList.add(webTheme);
            }
        }
    }

    public static boolean updateWebTheme(String friendlyName, String navBarColor, String headerLineColor, String editFormColor, String fullScreenBorderColor, String gradientLightColor, String gradientDarkColor)
        throws SQLException
    {
        WebTheme updateTheme = new WebTheme (friendlyName, navBarColor, headerLineColor, editFormColor, fullScreenBorderColor, gradientLightColor, gradientDarkColor);
        WebTheme.updateWebTheme(updateTheme);

        saveWebThemes();

        return true;
    }

    public static void deleteWebTheme(String friendlyName) throws SQLException
    {
        if (null == friendlyName || 0 == friendlyName.length()) return;

        boolean themeFound = false;
        List<WebTheme> webThemeList = WebTheme.getWebThemes();
        for (int i = 0; i < webThemeList.size(); i++)
        {
            WebTheme theme = webThemeList.get(i);
            if (theme.getFriendlyName().equals(friendlyName))
            {
                themeFound = true;

                //if this is the current theme, set the current theme to the default and save proactively.
                if (_theme.equals(theme))
                {
                    _theme = DEFAULT_THEME;
                    WriteableLookAndFeelAppProps props = WriteableLookAndFeelAppProps.getWriteableInstance(ContainerManager.getRoot());
                    props.setThemeName(_theme.getFriendlyName());
                    props.save();
                }

                _webThemeList.remove(i);

                break;
            }
        }
        if (themeFound)
        {
            WebTheme.saveWebThemes();
        }
    }

    public static void saveWebThemes() throws SQLException
    {
        // let's save the changes
        PropertyManager.PropertyMap properties = AppProps.getWebThemeConfigProperties();

        // keep a copy of all the themes
        {
            StringBuffer buffer = new StringBuffer();
            boolean firstEntry = true;
            for (WebTheme theme : _webThemeList)
            {
                if (firstEntry)
                {
                    firstEntry = false;
                }
                else
                {
                    buffer.append(";");
                }
                buffer.append(theme.getFriendlyName());
            }
            properties.put(THEME_NAMES_KEY, buffer.toString());
        }

        // let's update the theme's definition
        for (WebTheme theme : _webThemeList)
        {
            StringBuffer key = new StringBuffer();
            key.append(THEME_COLORS_KEY);
            key.append(theme.getFriendlyName());
            StringBuffer buffer = new StringBuffer();
            buffer.append(theme.getNavBarColor ());
            buffer.append(";").append(theme.getHeaderLineColor());
            buffer.append(";").append(theme.getEditFormColor());
            buffer.append(";").append(theme.getFullScreenBorderColor());
            buffer.append(";").append(theme.getGradientLightString());
            buffer.append(";").append(theme.getGradientDarkString());
            properties.put(key.toString (), buffer.toString ());
        }
        PropertyManager.saveProperties(properties);
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

