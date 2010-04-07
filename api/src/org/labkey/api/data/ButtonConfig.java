package org.labkey.api.data;

import org.labkey.api.util.URLHelper;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 7, 2010
 * Time: 10:15:25 AM
 */

/**
 * Represents configuration information for a specific button in a button bar configuration.
 * Currently this is used only with the QueryWebPart
 */
public class ButtonConfig
{
    private String _text;
    private String _url;
    private String _onClick;

    public String getText()
    {
        return _text;
    }

    public void setText(String text)
    {
        _text = text;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(String url)
    {
        _url = url;
    }

    public String getOnClick()
    {
        return _onClick;
    }

    public void setOnClick(String onClick)
    {
        _onClick = onClick;
    }

    public ActionButton createButton()
    {
        ActionButton btn = new ActionButton(_text);
        URLHelper url = null;
        if (null != _url)
            btn.setURL(_url);
        if (null != _onClick)
            btn.setScript(_onClick, false);

        return btn;
    }
}
