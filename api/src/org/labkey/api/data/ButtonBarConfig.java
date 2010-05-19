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
import org.labkey.data.xml.*;

import java.util.ArrayList;
import java.util.List;

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
    private String[] _scriptIncludes;

    public ButtonBarConfig()
    {
    }
    
    public ButtonBarConfig(JSONObject json)
    {
        if (json.has("position") && null != json.getString("position"))
            _position = getPosition(json.getString("position"));

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

    public ButtonBarConfig(ButtonBarOptions buttonBarOptions)
    {
        if (buttonBarOptions.isSetPosition())
        {
            _position = getPosition(buttonBarOptions.getPosition().toString());
        }
        _includeStandardButtons = buttonBarOptions.getIncludeStandardButtons();
        _scriptIncludes = buttonBarOptions.getIncludeScriptArray();
        ButtonBarItem[] items = buttonBarOptions.getItemArray();
        if (items != null && items.length > 0)
        {
            _items = new ArrayList<ButtonConfig>();
            for (ButtonBarItem item : items)
                _items.add(loadButtonConfig(item));
        }
    }

    protected ButtonConfig loadButtonConfig(ButtonBarItem item)
    {
        UserDefinedButtonConfig buttonConfig = new UserDefinedButtonConfig();
        buttonConfig.setText(item.getText());
        if (item.getTarget() != null && item.getTarget().getStringValue() != null)
        {
            buttonConfig.setUrl(item.getTarget().getStringValue());
            ActionButton.Action method = ActionButton.Action.GET;
            try
            {
                if (item.getTarget().getMethod() != null)
                    method = ActionButton.Action.valueOf(item.getTarget().getMethod().toString());
            }
            catch (IllegalArgumentException e)
            {
                // Unfortunately, XMLBeans throws an XmlValueOutOfRangeException on access of getMethod if the user
                // has provided an invalid enum value for the method.
            }
            buttonConfig.setAction(method);
        }
        buttonConfig.setOnClick(item.getOnClick());
        buttonConfig.setRequiresSelection(item.getRequiresSelection());

        ButtonMenuItem[] subItems = item.getItemArray();
        if (subItems != null && subItems.length > 0)
        {
            List<NavTree> menuItems = new ArrayList<NavTree>();
            for (ButtonMenuItem subItem : subItems)
                menuItems.add(loadNavTree(subItem));
            buttonConfig.setMenuItems(menuItems);
        }
        return buttonConfig;
    }

    private NavTree loadNavTree(ButtonMenuItem item)
    {
        NavTree tree = new NavTree(item.getText(), item.getTarget());
        if (item.getOnClick() != null && item.getOnClick().length() > 0)
            tree.setScript(item.getOnClick());
        if (item.getIcon() != null && item.getIcon().length() > 0)
            tree.setImageSrc(item.getIcon());
        if (item.getItemArray() != null && item.getItemArray().length > 0)
        {
            for (ButtonMenuItem child : item.getItemArray())
                tree.addChild(loadNavTree(child));
        }
        return tree;
    }

    private DataRegion.ButtonBarPosition getPosition(String position)
    {
        try
        {
            return DataRegion.ButtonBarPosition.valueOf(position.toUpperCase());
        }
        catch (Exception e)
        {
            throw new RuntimeException("'" + position + "' is not a valid button bar position (top, bottom, both, none).");
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

    public String[] getScriptIncludes()
    {
        return _scriptIncludes;
    }

    public void setScriptIncludes(String[] scriptIncludes)
    {
        _scriptIncludes = scriptIncludes;
    }
}
