package org.labkey.api.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 8, 2010
 * Time: 11:06:49 AM
 */

/**
 * Represents configuration information for a specific button in a button bar configuration.
 */
public class UserDefinedButtonConfig implements ButtonConfig
{
    private String _text;
    private String _url;
    private String _onClick;
    private List<NavTree> _menuItems;

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

    public void setMenuItems(List<NavTree> items)
    {
        _menuItems = items;
    }

    public DisplayElement createButton(List<DisplayElement> originalButtons)
    {
        if (null != _menuItems)
        {
            MenuButton btn = new MenuButton(_text);
            for (NavTree item : _menuItems)
            {
                btn.addMenuItem(item);
            }
            return btn;
        }
        else
        {
            ActionButton btn = new ActionButton(_text);
            if (null != _url)
                btn.setURL(_url);
            if (null != _onClick)
                btn.setScript(_onClick, false);

            return btn;
        }
    }
}
