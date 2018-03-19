/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.view.NavTree;
import org.labkey.data.xml.ButtonBarItem;
import org.labkey.data.xml.ButtonBarOptions;
import org.labkey.data.xml.ButtonMenuItem;
import org.labkey.data.xml.PermissionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
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
    private List<ButtonConfig> _items = new ArrayList<>();
    private boolean _includeStandardButtons = false;
    private boolean _alwaysShowRecordSelectors = false;
    private String[] _scriptIncludes;
    private String _onRenderScript;
    private Set<String> _hiddenStandardButtons = new HashSet<>();

    private static final Logger LOG = Logger.getLogger(ButtonBarConfig.class);

    public ButtonBarConfig(JSONObject json)
    {
        if (json.has("position") && null != json.getString("position"))
            _position = getPosition(json.getString("position"));

        _includeStandardButtons = json.optBoolean("includeStandardButtons", false);
        boolean suppressWarnings = json.optBoolean("suppressWarnings", false);

        if (json.has("items"))
        {
            JSONArray items = json.getJSONArray("items");
            for (int idx = 0; idx < items.length(); ++idx)
            {
                Object item = items.get(idx);
                if (item instanceof String)
                {
                    BuiltInButtonConfig config = new BuiltInButtonConfig((String)item);
                    config.setSuppressWarning(suppressWarnings);
                    _items.add(config);
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

                    String permissionString = obj.getString("permission");

                    if ("READ".equalsIgnoreCase(permissionString))
                    {
                        button.setPermission(ReadPermission.class);
                    }
                    else if ("INSERT".equalsIgnoreCase(permissionString))
                    {
                        button.setPermission(InsertPermission.class);
                    }
                    else if ("UPDATE".equalsIgnoreCase(permissionString))
                    {
                        button.setPermission(UpdatePermission.class);
                    }
                    else if ("DELETE".equalsIgnoreCase(permissionString))
                    {
                        button.setPermission(DeletePermission.class);
                    }
                    else if ("ADMIN".equalsIgnoreCase(permissionString))
                    {
                        button.setPermission(AdminPermission.class);
                    }
                    else if (json.getString("permissionClass") != null)
                    {
                        // permission has precedence, but if it's not specified look at permissionClass instead
                        button.setPermission(getPermissionClass(json.getString("permissionClass")));
                    }


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
            _items = new ArrayList<>();
            for (ButtonBarItem item : items)
                _items.add(loadButtonConfig(item));
        }
        if (buttonBarOptions.isSetOnRender())
        {
            _onRenderScript = buttonBarOptions.getOnRender();
        }
        if (buttonBarOptions.isSetAlwaysShowRecordSelectors())
        {
            _alwaysShowRecordSelectors = buttonBarOptions.getAlwaysShowRecordSelectors();
        }
    }

    public ButtonBarConfig(ButtonBarConfig cfg)
    {
        this(new JSONObject());
        if (cfg.getPosition() != null)
            this.setPosition(cfg.getPosition());

        this.setIncludeStandardButtons(cfg.isIncludeStandardButtons());
        if (cfg.getScriptIncludes() != null)
            this.setScriptIncludes(Arrays.copyOfRange(cfg.getScriptIncludes(), 0 , cfg.getScriptIncludes().length));

        this._hiddenStandardButtons = new HashSet<>(cfg._hiddenStandardButtons);
        this._onRenderScript = cfg._onRenderScript;
        this.setAlwaysShowRecordSelectors(cfg.isAlwaysShowRecordSelectors());

        if (cfg.getItems() != null)
        {
            List<ButtonConfig> items = new ArrayList<>();
            for (ButtonConfig btn : cfg.getItems())
            {
                items.add(btn.clone());
            }
            this.setItems(items);
        }
    }

    protected ButtonConfig loadButtonConfig(ButtonBarItem item)
    {
        if (item.getOriginalText() != null)
        {
            BuiltInButtonConfig buttonConfig = new BuiltInButtonConfig(item.getOriginalText(), item.getText());
            if (item.isSetHidden())
            {
                buttonConfig.setHidden(item.getHidden());
                if (item.getHidden())
                {
                    _hiddenStandardButtons.add(item.getOriginalText());
                }
            }
            if (item.isSetIconCls())
            {
                buttonConfig.setIconCls(item.getIconCls());
            }
            if (item.isSetSuppressWarning())
            {
                buttonConfig.setSuppressWarning(item.getSuppressWarning());
            }
            if (item.isSetInsertBefore())
                buttonConfig.setInsertBefore(item.getInsertBefore());
            else if (item.isSetInsertAfter())
                buttonConfig.setInsertAfter(item.getInsertAfter());
            else if (item.isSetInsertPosition())
            {
                Object position = item.getInsertPosition();
                if (position instanceof Integer)
                    buttonConfig.setInsertPosition(((Integer)position));
                else if ("beginning".equals(position))
                    buttonConfig.setInsertPosition(0);
                else if ("end".equals(position))
                    buttonConfig.setInsertPosition(-1);
            }
            buttonConfig.setPermission(getPermission(item));

            return buttonConfig;
        }
        else
        {
            UserDefinedButtonConfig buttonConfig = new UserDefinedButtonConfig();
            buttonConfig.setText(item.getText());
            if (item.isSetIconCls())
                buttonConfig.setIconCls(item.getIconCls());
            if (item.isSetInsertBefore())
                buttonConfig.setInsertBefore(item.getInsertBefore());
            else if (item.isSetInsertAfter())
                buttonConfig.setInsertAfter(item.getInsertAfter());
            else if (item.isSetInsertPosition())
            {
                Object position = item.getInsertPosition();
                if (position instanceof Integer)
                    buttonConfig.setInsertPosition(((Integer)position));
                else if ("beginning".equals(position))
                    buttonConfig.setInsertPosition(0);
                else if ("end".equals(position))
                    buttonConfig.setInsertPosition(-1);
            }

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
            buttonConfig.setOnClick(StringUtils.trimToNull(item.getOnClick()));
            buttonConfig.setRequiresSelection(item.getRequiresSelection());

            if (item.getRequiresSelectionMinCount() > 0)
                buttonConfig.setRequiresSelectionMinCount(item.getRequiresSelectionMinCount());
            if (item.getRequiresSelectionMaxCount() > 0)
                buttonConfig.setRequiresSelectionMaxCount(item.getRequiresSelectionMaxCount());

            buttonConfig.setPermission(getPermission(item));

            ButtonMenuItem[] subItems = item.getItemArray();
            if (subItems != null && subItems.length > 0)
            {
                List<NavTree> menuItems = new ArrayList<>();
                for (ButtonMenuItem subItem : subItems)
                    menuItems.add(loadNavTree(subItem));
                buttonConfig.setMenuItems(menuItems);
            }

            return buttonConfig;
        }
    }

    private Class<? extends Permission> getPermission(ButtonBarItem item)
    {
        if (item.getPermission() == PermissionType.READ)
        {
            return ReadPermission.class;
        }
        else if (item.getPermission() == PermissionType.INSERT)
        {
            return InsertPermission.class;
        }
        else if (item.getPermission() == PermissionType.UPDATE)
        {
            return UpdatePermission.class;
        }
        else if (item.getPermission() == PermissionType.DELETE)
        {
            return DeletePermission.class;
        }
        else if (item.getPermission() == PermissionType.ADMIN)
        {
            return AdminPermission.class;
        }
        else if (item.getPermissionClass() != null)
        {
            // permission has precedence, but if it's not specified look at permissionClass instead
            return getPermissionClass(item.getPermissionClass());
        }
        else
        {
            return null;
        }

    }

    private Class<? extends Permission> getPermissionClass(String permissionClassName)
    {
        try
        {
            Class c = Class.forName(permissionClassName);
            if (Permission.class.isAssignableFrom(c))
            {
                return (Class<? extends Permission>) c;
            }
            else
            {
                LOG.warn("Resolved class " + permissionClassName + " but it was not of the expected type, " + Permission.class);
            }
        }
        catch (ClassNotFoundException e)
        {
            LOG.warn("Could not find permission class " + permissionClassName);
        }
        return null;
    }

    private NavTree loadNavTree(ButtonMenuItem item)
    {
        NavTree tree = new NavTree(item.getText(), item.getTarget());
        if (item.getOnClick() != null && item.getOnClick().length() > 0)
            tree.setScript(item.getOnClick());
        if (item.getIcon() != null && item.getIcon().length() > 0)
            tree.setImageSrc(new ResourceURL(item.getIcon()));
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
            throw new RuntimeException("'" + position + "' is not a valid button bar position (top, none).");
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

        return root.hasChildren() ? root.getChildren() : null;
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

    public Set<String> getHiddenStandardButtons()
    {
        return _hiddenStandardButtons;
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

    public String getOnRenderScript()
    {
        return _onRenderScript;
    }

    public void setOnRenderScript(String onRenderScript)
    {
        _onRenderScript = onRenderScript;
    }

    public boolean isAlwaysShowRecordSelectors()
    {
        return _alwaysShowRecordSelectors;
    }

    public void setAlwaysShowRecordSelectors(boolean alwaysShowRecordSelectors)
    {
        _alwaysShowRecordSelectors = alwaysShowRecordSelectors;
    }
}
