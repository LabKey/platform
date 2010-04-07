package org.labkey.api.data;

import org.apache.commons.beanutils.BeanUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 7, 2010
 * Time: 10:13:01 AM
 */

/**
 * Represents a custom button bar configuration passed from the client
 * Currently this is used only from a QueryWebPart
 */
public class ButtonBarConfig
{
    public enum Position
    {
        top,
        bottom,
        both
    }

    public enum BuiltInButton
    {
        query,
        views,
        export,
        pageSize,
        print
    }

    private Position _position = null; //i.e., not specified
    private List<Object> _items = new ArrayList<Object>();

    public ButtonBarConfig()
    {
    }
    
    public ButtonBarConfig(JSONObject json)
    {
        if (json.has("position"))
        {
            try
            {
                _position = Position.valueOf(json.getString("position"));
            }
            catch (Exception ignore)
            {
                throw new RuntimeException("'" + json.getString("position") + "' is not a valid button bar position.");
            }
        }

        if (json.has("items"))
        {
            JSONArray items = json.getJSONArray("items");
            for (int idx = 0; idx < items.length(); ++idx)
            {
                Object item = items.get(idx);
                if (item instanceof String)
                {
                    try
                    {
                        _items.add(BuiltInButton.valueOf((String)item));

                    }
                    catch(Exception e)
                    {
                        throw new RuntimeException("'" + item + "' is not a valid built-in button name.");
                    }
                }
                else if (item instanceof JSONObject)
                {
                    //new button config
                    ButtonConfig button = new ButtonConfig();
                    try
                    {
                        BeanUtils.populate(button, (Map)item);
                    }
                    catch (Exception ignore) {}
                    _items.add(button);
                }
            }
        }
    }

    public Position getPosition()
    {
        return _position;
    }

    public void setPosition(Position position)
    {
        _position = position;
    }

    public List<Object> getItems()
    {
        return _items;
    }

    public void setItems(List<Object> items)
    {
        _items = items;
    }
}
