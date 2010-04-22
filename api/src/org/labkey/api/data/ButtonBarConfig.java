/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.beanutils.BeanUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.NavTree;

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
    private DataRegion.ButtonBarPosition _position = null; //i.e., not specified
    private List<ButtonConfig> _items = new ArrayList<ButtonConfig>();
    private boolean _includeStandardButtons = false;

    public ButtonBarConfig()
    {
    }
    
    public ButtonBarConfig(JSONObject json)
    {
        if (json.has("position") && null != json.getString("position"))
        {
            try
            {
                _position = DataRegion.ButtonBarPosition.valueOf(json.getString("position").toUpperCase());
            }
            catch (Exception ignore)
            {
                throw new RuntimeException("'" + json.getString("position") + "' is not a valid button bar position (top, bottom, both, none).");
            }
        }

        _includeStandardButtons = json.optBoolean("includeStandardButtons", false);

        if (json.has("items"))
        {
            JSONArray items = json.getJSONArray("items");
            for (int idx = 0; idx < items.length(); ++idx)
            {
                Object item = items.get(idx);
                if (item instanceof String)
                {
                    _items.add(new BuiltInButtonConfig((String)item));
                }
                else if (item instanceof JSONObject)
                {
                    JSONObject obj = (JSONObject)item;
                    //new button config
                    UserDefinedButtonConfig button = new UserDefinedButtonConfig();
                    try
                    {
                        BeanUtils.populate(button, obj);
                    }
                    catch (Exception ignore) {}

                    //if the object has an "items" prop, load those as NavTree
                    //items recursively (for menu buttons)
                    if (obj.has("items"))
                        button.setMenuItems(loadMenuItems(obj.getJSONArray("items")));

                    _items.add(button);
                }
            }
        }
    }

    protected List<NavTree> loadMenuItems(JSONArray items)
    {
        NavTree root = new NavTree();

        for (int idx = 0; idx < items.length(); ++idx)
        {
            Object item = items.get(idx);

            //item can be a string (separator), or a map (menu item)
            // and the map may contain an items array of its own (fly-out menu)
            if (item instanceof String)
            {
                String sitem = (String)item;
                if (sitem.equals("-"))
                    root.addSeparator();
            }
            else if (item instanceof JSONObject)
            {
                JSONObject obj = (JSONObject)item;
                NavTree nt = new NavTree(obj.getString("text"));
                if (obj.has("onClick"))
                    nt.setScript(obj.getString("onClick"));
                if (obj.has("icon"))
                    nt.setImageSrc(obj.getString("icon"));

                if (obj.has("items"))
                    nt.addChildren(loadMenuItems(obj.getJSONArray("items")));

                root.addChild(nt);
            }
        }

        return root.hasChildren() ? root.getChildList() : null;
    }

    public DataRegion.ButtonBarPosition getPosition()
    {
        return _position;
    }

    public void setPosition(DataRegion.ButtonBarPosition position)
    {
        _position = position;
    }

    public List<ButtonConfig> getItems()
    {
        return _items;
    }

    public void setItems(List<ButtonConfig> items)
    {
        _items = items;
    }

    public boolean isIncludeStandardButtons()
    {
        return _includeStandardButtons;
    }

    public void setIncludeStandardButtons(boolean includeStandardButtons)
    {
        _includeStandardButtons = includeStandardButtons;
    }
}
