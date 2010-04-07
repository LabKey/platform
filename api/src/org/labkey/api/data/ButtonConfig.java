package org.labkey.api.data;

import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

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


    public ActionButton createButton()
    {
        URLHelper url = null;
        try
        {
            url = new URLHelper(_url);
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        return new ActionButton(_text, url);
    }
}
