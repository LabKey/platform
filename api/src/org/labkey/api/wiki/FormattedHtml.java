package org.labkey.api.wiki;

/**
 * User: adam
 * Date: Aug 6, 2007
 * Time: 2:18:32 PM
 */
public class FormattedHtml
{
    private String _html;

    // Indicates that rendered HTML can change even if passed in content remains static.  This can happen when
    // renderer uses external resources, for example, URL parameters pulled from ThreadLocal, AppProps, etc.
    // If the formatted HTML is volatile, we shouldn't cache the formatted contents.
    private boolean _volatile;


    private FormattedHtml()
    {

    }

    public FormattedHtml(String html)
    {
        this(html, false);
    }

    public FormattedHtml(String html, boolean isVolatile)
    {
        _html = html;
        _volatile = isVolatile;
    }

    public String getHtml()
    {
        return _html;
    }

    public void setHtml(String html)
    {
        _html = html;
    }

    public boolean isVolatile()
    {
        return _volatile;
    }

    public void setVolatile(boolean aVolatile)
    {
        _volatile = aVolatile;
    }
}
