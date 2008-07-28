/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * User: mbellew
 * Date: Aug 30, 2005
 * Time: 2:42:48 PM
 */
public class ButtonServlet extends HttpServlet
{
    public enum Style
    {
        DEFAULT,    // default is reserved
        disabled,
        large,
        tab,
        clearTab,
        selectedTab,
        inactiveTab,
        shadedMenu,
        whiteMenu,
        boldMenu
    }


    static Map<Button, Button> buttonMap = Collections.synchronizedMap(new LinkedHashMap<Button, Button>(150)
    {
        public boolean removeEldestEntry(Map.Entry entry)
        {
            return size() > 100;
        }
    });

    static String tabTextHeight;     // initialize in initButtonStyles()
    static String buttonTextHeight;  // initialize in initButtonStyles()
    static HashMap<Style, Button> buttonStyles = initButtonStyles();
    static boolean tabHeightDirty = true;
    static int tabHeight = computeTabHeight ();

    public static void resetColorScheme()
    {
        buttonMap.clear();
        buttonStyles = initButtonStyles();
        tabHeightDirty = true;
        tabHeight = computeTabHeight ();
    }

    static HashMap<Style, Button> initButtonStyles()
    {
        WebTheme theme = WebTheme.getTheme();
        ThemeFont themeFont = ThemeFont.getThemeFont();
        tabTextHeight = themeFont.getGraphicTabTextHeight();
        buttonTextHeight = themeFont.getGraphicButtonTextHeight();
        Button.setDefaultFont();

        Color headerLineColor = Color.BLACK;
        try
        {
            headerLineColor = new Color(Integer.parseInt(theme.getHeaderLineColor(), 16));
        }
        catch (Exception x)
        {
            // 
        }

        HashMap<Style, Button> m = new HashMap<Style, Button>();

        // default
        Button defaultButton = new Button();
        defaultButton.setFont(Button.defaultFont);
        m.put(Style.DEFAULT, defaultButton);

        // disables
        Button disabledButton = new Button();
        disabledButton.setFont(Button.defaultFont);
        disabledButton.setBackground(new Color(0xf4, 0xf4, 0xf4));
        disabledButton.setBorder(Color.GRAY);
        disabledButton.setForeground(Color.GRAY);
        m.put(Style.disabled, disabledButton);

        // large button
        Button largeButton = new Button();
        largeButton.setFont(Font.decode("verdana plain "+tabTextHeight));
        largeButton.setForeground(Color.BLACK);
        largeButton.setLeftMargin(10);
        largeButton.setRightMargin(10);
        m.put(Style.large, largeButton);

        // tabs
        Button tabDefault = new Tab();
        tabDefault.setFont(Font.decode("verdana plain "+tabTextHeight));
        tabDefault.setSizingFont(Font.decode("verdana bold "+tabTextHeight));
        tabDefault.setBorder(headerLineColor);
        m.put(Style.tab, tabDefault);

        Button tabClear = new Tab();
        tabClear.setFont(Font.decode("verdana plain "+tabTextHeight));
        tabDefault.setSizingFont(Font.decode("verdana bold "+tabTextHeight));
        tabClear.setBackground(new Color(0x00ffffff, true));
        tabDefault.setBorder(headerLineColor);
        m.put(Style.clearTab, tabDefault);

        Button tabSelected = new Tab();
        tabSelected.setFont(Font.decode("verdana bold "+tabTextHeight));
        tabDefault.setSizingFont(Font.decode("verdana bold "+tabTextHeight));
//        tabSelected.setBackground(new Color(0xffd275));
        tabSelected.setBorder(headerLineColor);
        m.put(Style.selectedTab, tabSelected);

        Button tabInactive = new Tab();
        tabInactive.setFont(Font.decode("verdana plain "+tabTextHeight));
        tabDefault.setSizingFont(Font.decode("verdana bold "+tabTextHeight));
        tabInactive.setForeground(new Color(0xcccccc));
        tabInactive.setBorder(headerLineColor);
        m.put(Style.inactiveTab, tabInactive);

        m.put(Style.shadedMenu, new DropDown());

        DropDown grayDropDown = new DropDown();
        grayDropDown.setBackground(Color.WHITE);
        grayDropDown.setForeground(new Color(0x003399));
        grayDropDown.setBorder(Color.GRAY);
        m.put(Style.whiteMenu, grayDropDown);

        DropDown boldDropDown = new DropDown();
        boldDropDown.setFont(Font.decode("verdana bold "+buttonTextHeight));
        boldDropDown.setBackground(Color.WHITE);
        boldDropDown.setBackgroundGradient(null);
        boldDropDown.setForeground(new Color(0x003399));
        boldDropDown.setBorder(null);
        grayDropDown.setRoundCorners(false);
        m.put(Style.boldMenu, boldDropDown);

        return m;
    }

    static int computeTabHeight ()
    {
        if (!tabHeightDirty)
            return tabHeight;

        // just a best-guess value
        int height = (int) java.lang.Math.floor(Integer.parseInt(tabTextHeight)*1.4) +  getTabCornerHeight();

        Button template = buttonStyles.get(Style.tab);
        if (null != template)
        {
            Button button = template.copy();
            if (null != button)
            {
                button.setText("Gg");
                height = button.getHeight();
            }
        }

        tabHeight = height;
        tabHeightDirty = false;
        
        return height;
    }

    public static int getTabHeight ()
    {
        return tabHeight;
    }

    public static int getTabCornerHeight ()
    {
        return 4;
    }

    public static class Button implements Cloneable
    {
        static String imageType = "png";
        static String mimeType = "image/png";
        static Font defaultFont = Font.decode("verdana plain "+buttonTextHeight);

        protected Color _clear = Color.WHITE; // to draw transparent pixels, use g.setComposite(AlphaComposite.Clear)
        protected Color _foreground = Color.BLACK;
        protected Color _background = new Color(0xe3, 0xe3, 0xe3);
        protected Color _backgroundGradient = new Color(0xf0, 0xf0, 0xf0);
        protected Color _border = Color.BLACK;
        protected Color _borderInset = new Color(0xf9, 0xf9, 0xf9);
        protected String _text = "Submit";
        protected Font _font = defaultFont;
        protected int _leftMargin = 5;
        protected int _rightMargin = 5;
        protected int _topMargin = 2;
        protected int _paddingRight = 0;    // additional padding
        protected int _bottomMargin = 2;
        protected boolean _roundCorners = true;

        byte[] _bytes = null;

        private int _height = -1;
        private int _width = -1;
        private Font _sizingFont;

        private static void setDefaultFont ()
        {
            defaultFont = Font.decode("verdana plain "+buttonTextHeight);
        }

        public String getType()
        {
            return mimeType;
        }

        public synchronized byte[] getBytes()
        {
            if (null != _bytes)
                return _bytes;

            // TODO: use 8-bit image type?
            BufferedImage bi = new BufferedImage(1000, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g;
            try
            {
                g = bi.createGraphics(); // If there's no X server on Linux, this explodes
            }
            catch (InternalError ie)
            {
                // Let's create a friendlier message so the user might be able to fix the problem
                throw new RuntimeException(ie.toString() + "\nPlease Edit tomcat's catalina.sh file, and add the following line near the top of the file:\n" +
                        "\n" +
                        "CATALINA_OPTS=\"-Djava.awt.headless=true\"\n" +
                        "\n" +
                        "Then restart tomcat.");
            }

            if (null != _sizingFont)
                g.setFont(_sizingFont);
            else if (null != _font)
                g.setFont(_font);
            else
                _font = g.getFont();

            FontMetrics fm = g.getFontMetrics();
            Rectangle2D r = fm.getStringBounds(_text, g);
            _width = (int) java.lang.Math.ceil(r.getWidth()) + _leftMargin + _rightMargin + _paddingRight;
            _height = fm.getMaxDescent() + fm.getMaxAscent() + _topMargin + _bottomMargin;
            int baseline = fm.getMaxDescent() + _bottomMargin;
            g.dispose();

            // TODO: how do I create an image of the right size without knowing
            // the string size, and how do I know the string size without
            // creating a bufferred image?

            bi = new BufferedImage(_width, _height, BufferedImage.TYPE_INT_ARGB);
            g = bi.createGraphics();
            g.setFont(_font);

            // background
            if (_backgroundGradient != null)
            {
                Paint backgroundPaint = new GradientPaint(
                        0, _topMargin, _backgroundGradient,
                        0, _height - _bottomMargin, _background,
                        true);
                g.setPaint(backgroundPaint);
                g.fill(new Rectangle(0, 0, _width, _height));
            }
            else
            {
                g.setBackground(_background);
                g.clearRect(0, 0, _width, _height);
            }

            // text
            // antialiasing with VALUE_TEXT_ANTIALIAS_LCD_HRGB looks better but is jdk1.6
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setColor(_foreground);
            g.drawString(_text, _leftMargin, _height - baseline);

            customDraw(g, _width, _height);
            g.dispose();

            try
            {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                //writeImage(bi, imageType, out);
                ImageIO.write(bi, imageType, out);
                _bytes = out.toByteArray();
            }
            catch (IOException x)
            {
                Logger.getLogger(ButtonServlet.class).error("unexpected error", x);
            }
            return _bytes;
        }
        

        protected void customDraw(Graphics2D g, int width, int height)
        {
            // border
            if (null != _border)
            {
                g.setColor(_border);
                g.drawRect(0, 0, _width - 1, _height - 1);

                g.setColor(_borderInset);
                g.drawRect(1, 1, _width - 3, _height - 3);
            }

            // corners
            if (_roundCorners)
            {
                Composite composite = g.getComposite();
                g.setComposite(AlphaComposite.Clear);
                g.setColor(_clear);
                g.drawLine(0, 0, 0, 0);
                g.drawLine(0, _height - 1, 0, _height - 1);
                g.drawLine(_width - 1, 0, _width - 1, 0);
                g.drawLine(_width - 1, _height - 1, _width - 1, _height - 1);
                g.setComposite(composite);
            }
        }


        public int getHeight()
        {
            if (_height == -1)
                getBytes();
            return _height;
        }


        public int getWidth()
        {
            if (_width == -1)
                getBytes();
            return _width;
        }


        public Color getForeground()
        {
            return _foreground;
        }


        public void setForeground(Color foreground)
        {
            _foreground = foreground;
        }


        public Color getBackground()
        {
            return _background;
        }


        public void setBackground(Color background)
        {
            _background = background;
        }


        public Color getBackgroundGradient()
        {
            return _backgroundGradient;
        }

        public void setBackgroundGradient(Color backgroundGradient)
        {
            _backgroundGradient = backgroundGradient;
        }


        public Color getBorder()
        {
            return _border;
        }


        public void setBorder(Color border)
        {
            _border = border;
        }


        public String getText()
        {
            return _text;
        }


        public void setText(String text)
        {
            _text = text;
        }


        public Font getFont()
        {
            return _font;
        }


        public void setFont(Font f)
        {
            _font = f;
        }


        public int getLeftMargin()
        {
            return _leftMargin;
        }


        public void setLeftMargin(int x)
        {
            _leftMargin = x;
        }


        public int getRightMargin()
        {
            return _rightMargin;
        }


        public void setRightMargin(int x)
        {
            _rightMargin = x;
        }


        public int getTopMargin()
        {
            return _topMargin;
        }

        public void setTopMargin(int margin)
        {
            _topMargin = margin;
        }

        public int getBottomMargin()
        {
            return _bottomMargin;
        }

        public void setBottomMargin(int margin)
        {
            _bottomMargin = margin;
        }

        public boolean getRoundCorners()
        {
            return _roundCorners;
        }

        public void setRoundCorners(boolean r)
        {
            _roundCorners = r;
        }

        public Button copy()
        {
            try
            {
                return (Button)super.clone();
            }
            catch (CloneNotSupportedException x)
            {
                return null;
            }
        }

        public void setSizingFont(Font font)
        {
            _sizingFont = font;
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Button button = (Button) o;

            // _height, _width, and _bytes are computed

            if (_bottomMargin != button._bottomMargin) return false;
            if (_leftMargin != button._leftMargin) return false;
            if (_paddingRight != button._paddingRight) return false;
            if (_rightMargin != button._rightMargin) return false;
            if (_roundCorners != button._roundCorners) return false;
            if (_topMargin != button._topMargin) return false;
            if (_background != null ? !_background.equals(button._background) : button._background != null)
                return false;
            if (_backgroundGradient != null ? !_backgroundGradient.equals(button._backgroundGradient) : button._backgroundGradient != null)
                return false;
            if (_border != null ? !_border.equals(button._border) : button._border != null) return false;
            if (_borderInset != null ? !_borderInset.equals(button._borderInset) : button._borderInset != null) return false;
            if (_clear != null ? !_clear.equals(button._clear) : button._clear != null) return false;
            if (_font != null ? !_font.equals(button._font) : button._font != null) return false;
            if (_foreground != null ? !_foreground.equals(button._foreground) : button._foreground != null)
                return false;
            if (_sizingFont != null ? !_sizingFont.equals(button._sizingFont) : button._sizingFont != null)
                return false;
            if (_text != null ? !_text.equals(button._text) : button._text != null) return false;

            return true;
        }

        public int hashCode()
        {
            int result;
            result = (_clear != null ? _clear.hashCode() : 0);
            result = 31 * result + (_foreground != null ? _foreground.hashCode() : 0);
            result = 31 * result + (_background != null ? _background.hashCode() : 0);
            result = 31 * result + (_backgroundGradient != null ? _backgroundGradient.hashCode() : 0);
            result = 31 * result + (_border != null ? _border.hashCode() : 0);
            result = 31 * result + (_borderInset != null ? _borderInset.hashCode() : 0);
            result = 31 * result + (_text != null ? _text.hashCode() : 0);
            result = 31 * result + (_font != null ? _font.hashCode() : 0);
            result = 31 * result + _leftMargin;
            result = 31 * result + _rightMargin;
            result = 31 * result + _topMargin;
            result = 31 * result + _paddingRight;
            result = 31 * result + _bottomMargin;
            result = 31 * result + (_roundCorners ? 1 : 0);
            result = 31 * result + (_sizingFont != null ? _sizingFont.hashCode() : 0);
            return result;
        }
    }


    public static class Tab extends Button
    {
        boolean _bottomBorder = false;

        public Tab()
        {
            super();
            setForeground(new Color(0x003399));
            setBackground(Color.WHITE);
            setTopMargin(2);
            setBottomMargin(2);
            setLeftMargin(8);
            setRightMargin(12);
            setRoundCorners(false);
        }


        protected void customDraw(Graphics2D g, int width, int height)
        {
            if (null != _border)
            {
                // fill in the corner
                Color cornerColor = (_background.equals(_clear)) ? Color.WHITE : _clear;
                g.setColor(cornerColor);
                int corner = 4;
                for (int i = 0; i < corner; i++)
                    g.drawLine(width - corner + i, i, width - 1, i);

                // border (except right border)
                g.setColor(_border);
                g.drawLine(0, 0, width - corner, 0);              // top
                g.drawLine(width - corner, 0, width - 1, corner - 1);    // right-upper
            }
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            Tab tab = (Tab) o;

            if (_bottomBorder != tab._bottomBorder) return false;

            return true;
        }

        public int hashCode()
        {
            int result = super.hashCode();
            result = 31 * result + (_bottomBorder ? 1 : 0);
            return result;
        }
    }


    public static class DropDown extends Button
    {
        public DropDown()
        {
            _paddingRight = 11;
        }

        protected void customDraw(Graphics2D g, int width, int height)
        {
            super.customDraw(g, width, height);
            int[] x = new int[3];
            int[] y = new int[3];
            y[0] = height/2 - 1;
            y[1] = y[0];
            y[2] = y[0] + 4;
            x[0] = width - 13;
            x[1] = x[0] + 8;
            x[2] = x[0] + 4;
            g.setColor(getForeground());
            g.fillPolygon(x, y, 3);
        }


        public boolean equals(Object o)
        {
            return super.equals(o) && getClass() == o.getClass();
        }
    }


    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        // text
       String text = request.getParameter("text");
        if (null == text || 0 == text.length())
        {
            String uri = request.getRequestURI();
            String file = uri.substring(uri.lastIndexOf('/') + 1);
            if (file.lastIndexOf('.') > 0)
            {
                String name = file.substring(0, file.lastIndexOf('.'));
                text = PageFlowUtil.decode(name);
            }
        }
        if (null == text || 0 == text.length())
            text = "Submit";

        // style
        String styleName = request.getParameter("style");
        if (null == styleName || 0 == styleName.length() || styleName.equals("default"))
            styleName = "DEFAULT";
        Style style = null;
        try {style = Style.valueOf(styleName);} catch (IllegalArgumentException x){}
        Button template = buttonStyles.get(style);
        if (null == template)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        template = template.copy();

        template.setText(text);
        template = bindButton(template, request);
        Button button = buttonMap.get(template);
        if (null == button)
        {
            button = template;
            buttonMap.put(button, button);
        }

        byte[] bytes = button.getBytes();
        if (null == bytes)
        {   // shouldn't actually happen
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        response.reset();
        response.setContentType(button.getType());
        response.setContentLength(bytes.length);
        response.setHeader("Context-Disposition", "inline; filename=" + text + "." + Button.imageType);
        response.setDateHeader("Expires", System.currentTimeMillis() + DateUtils.MILLIS_PER_DAY * 5);
        response.getOutputStream().write(bytes);
    }


    Button bindButton(Button button, HttpServletRequest request)
    {
        HashMap<String,String> map = new HashMap<String, String>();
        Enumeration e = request.getParameterNames();
        while (e.hasMoreElements())
        {
            String key = (String)e.nextElement();
            map.put(key,request.getParameter(key));
        }
        return bindButton(button, map);
    }


    Button bindButton(Button button, Map<String,String> params)
    {
        params.remove("style");
        params.remove("text");
        if (params.isEmpty())
            return button;
        BeanObjectFactory f = (BeanObjectFactory)ObjectFactory.Registry.getFactory(button.getClass());
        f.fromMap(button, params);
        return button;
    }



    // using context path is not required, however, the browser
    // has fewer images to cache if we use a consistent path name
    static String getContextPath()
    {
        return AppProps.getInstance().getContextPath();
    }

    public static String submitSrc()
    {
        return getContextPath() + "/Submit.button?" + buttonTextHeight;
    }

    public static String cancelSrc()
    {
        return getContextPath() + "/Cancel.button?" + buttonTextHeight;
    }

    public static String buttonSrc(String name)
    {
        return getContextPath() + "/" + PageFlowUtil.encode(name) + ".button?" + buttonTextHeight;
    }

    // styles should not need encoding
    public static String buttonSrc(String name, String style)
    {
        AppProps appProps = AppProps.getInstance();
        return getContextPath() + "/" + PageFlowUtil.encode(name) + ".button?style=" + style + "&amp;" + buttonTextHeight + "&amp;revision=" + appProps.getLookAndFeelRevision();
    }
}
