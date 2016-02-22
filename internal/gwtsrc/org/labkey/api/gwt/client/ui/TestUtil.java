package org.labkey.api.gwt.client.ui;

public class TestUtil
{
    /**
     * Creates/updates signal element for WebDriver tests to track page status
     */
    public static native void signalWebDriver(String signal, Object value) /*-{
        $wnd.LABKEY.Utils.signalWebDriverTest(signal, value);
    }-*/;

    /**
     * Creates/updates signal element for WebDriver tests to track page status
     */
    public static native void signalWebDriver(String signal) /*-{
        $wnd.LABKEY.Utils.signalWebDriverTest(signal);
    }-*/;

}
