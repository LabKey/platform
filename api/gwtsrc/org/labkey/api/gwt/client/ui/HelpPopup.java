/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.InlineLabel;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: Karl Lum
 * Date: Aug 22, 2007
 */
public class HelpPopup extends InlineLabel
{
    private Element _element;
    private String _title;
    private String _body;

    public HelpPopup(String title, String body)
    {
        _element =  getElement();
        _title = title;
        _body = body;

        final String headerSize = PropertyUtil.getServerProperty("header1Size");
        String text = "<a tabindex=\"-1\" href=\"javascript:void(0);\"><span class=\"labkey-help-pop-up\" style=\"font-size:" +
                (headerSize == null ? "10pt" : headerSize) +
                ";\"><sup>?</sup></span></a>";
        DOM.setInnerHTML(_element, text);

        addMouseOverHandler(new MouseOverHandler()
        {
            public void onMouseOver(MouseOverEvent e)
            {
                showHelpDiv(_element, _title, _body);
            }
        });

        addMouseOutHandler(new MouseOutHandler()
        {
            public void onMouseOut(MouseOutEvent e)
            {
                hideHelpDiv();
            }
        });
    }

    public void setBody(String body)
    {
        _body = body;
    }

    /**
     * JSNI method to call the underlying javascript function in util.js
     */
    public static native void hideHelpDiv() /*-{
        $wnd.hideHelpDivDelay();
    }-*/;

    public static native void showHelpDiv(Element element, String title, String body) /*-{
        $wnd.showHelpDivDelay(element, title, body);
    }-*/;
}
