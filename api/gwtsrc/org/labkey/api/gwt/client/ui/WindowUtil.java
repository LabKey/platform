/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.Widget;

/**
 * User: Mark Igra
 * Date: Feb 26, 2007
 * Time: 9:56:50 PM
 */
public class WindowUtil
{
    /**
     * Gets the left scroll position.
     *
     * @return The left scroll position.
     */
    public static native int getScrollLeft() /*-{
            var scrollLeft;
            if ($wnd.innerHeight)
            {
                    scrollLeft = $wnd.pageXOffset;
            }
            else if ($doc.documentElement && $doc.documentElement.scrollLeft)
            {
                    scrollLeft = $doc.documentElement.scrollLeft;
            }
            else if ($doc.body)
            {
                    scrollLeft = $doc.body.scrollLeft;
            }
            return scrollLeft;
    }-*/;

    /**
     * Gets the top scroll position.
     *
     * @return The top scroll position.
     */
    public static native int getScrollTop() /*-{
            var scrollTop;
            if ($wnd.innerHeight)
            {
                    scrollTop = $wnd.pageYOffset;
            }
            else if ($doc.documentElement && $doc.documentElement.scrollTop)
            {
                    scrollTop = $doc.documentElement.scrollTop;
            }
            else if ($doc.body)
            {
                    scrollTop = $doc.body.scrollTop;
            }
            return scrollTop;
    }-*/;

    public static native void scrollTo(int x, int y) /*-{
        $wnd.scrollTo(x, y);
    }-*/;


    public static void scrollIntoView(Widget w)
    {
        int widgetTop = w.getAbsoluteTop();
        int widgetLeft = w.getAbsoluteLeft();
        int widgetWidth = w.getOffsetWidth();
        int widgetHeight = w.getOffsetHeight();
        int widgetRight = widgetLeft + widgetWidth;
        int widgetBottom = widgetTop + widgetHeight;

        int visTop = getScrollTop();
        int visLeft = getScrollLeft();
        int visWidth = Window.getClientWidth();
        int visHeight = Window.getClientHeight();
        int visRight = visLeft + visWidth;
        int visBottom = visTop + visHeight;

        if (widgetTop >= visTop && widgetBottom <= visBottom &&
                widgetLeft >= visLeft &&  widgetRight <= visRight)
            return;


        int newTop = visTop;
        int newLeft = visLeft;
        if (widgetTop < visTop || widgetHeight > visHeight)
            newTop = widgetTop;
        else if (widgetBottom > visBottom)
            newTop = widgetBottom - visHeight;

        if (widgetLeft < visLeft || widgetWidth > visWidth)
            newLeft = widgetLeft;
        else if (widgetRight > visRight)
            newLeft = widgetRight - visWidth;

        scrollTo(newLeft, newTop);
    }

    /**
     * Navigates to a different URL (leaving this app) using
     * window.location=loc
     * @param loc
     *
     * Does NOT work with simple action names like "begin.view" (this breaks on IE).  See PropertyUtil.getRelativeURL()
     * and PropertyUtil.getContextPath().
     */
    public static void setLocation(String loc)
    {
        Window.Location.assign(loc);
    }

    /**
     * Navigates back one page
     */
    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    public static void centerDialog(DialogBox dialogBox)
    {
        dialogBox.setPopupPosition((Window.getClientWidth() - dialogBox.getOffsetWidth()) / 2 + WindowUtil.getScrollLeft(), (Window.getClientHeight() - dialogBox.getOffsetHeight()) / 2 + WindowUtil.getScrollTop());
    }

    public static native String prompt(String prompt, String defaultValue) /*-{
        return $wnd.prompt(prompt, null == defaultValue ? "" : defaultValue);
    }-*/;

}
