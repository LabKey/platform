package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MouseListenerAdapter;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Aug 22, 2007
 */
public class HelpPopup extends Label
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
        String text = "<a href=\"javascript:void(0);\"><span style=\"font-weight:bold;font-size:" +
                (headerSize == null ? "10pt" : headerSize) +
                ";text-decoration:none;padding:0;\"><sup>?</sup></span></a>";
        DOM.setInnerHTML(_element, text);

        addMouseListener(new MouseListenerAdapter()
        {
            public void onMouseEnter(Widget sender)
            {
                showHelpDiv(_element, _title, _body);
            }

            public void onMouseLeave(Widget sender)
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
        $wnd.showHelpDiv(element, title, body);
    }-*/;
}
