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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents configuration information for a specific button in a button bar configuration.
 * User: dave
 * Date: Apr 8, 2010
 */
public class UserDefinedButtonConfig implements ButtonConfig
{
    private String _text;
    private String _iconCls;
    private String _url;
    private String _onClick;
    private ActionButton.Action _action;
    private boolean _requiresSelection = false;
    private Integer _requiresSelectionMinCount;
    private Integer _requiresSelectionMaxCount;
    /** Permission that a user must have in order to see the button */
    private Class<? extends Permission> _permission;
    private List<NavTree> _menuItems;
    private String _insertAfter, _insertBefore;
    private Integer _insertPosition;

    public String getText()
    {
        return _text;
    }

    public void setText(String text)
    {
        _text = text;
    }

    @Override
    public String getIconCls()
    {
        return _iconCls;
    }

    public void setIconCls(String iconCls)
    {
        _iconCls = iconCls;
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

    public List<NavTree> getMenuItems()
    {
        return _menuItems == null ? null : Collections.unmodifiableList(_menuItems);
    }

    public void setMenuItems(List<NavTree> items)
    {
        _menuItems = items;
    }

    public ActionButton.Action getAction()
    {
        return _action;
    }

    public void setAction(ActionButton.Action action)
    {
        _action = action;
    }

    public boolean isRequiresSelection()
    {
        return _requiresSelection;
    }

    public void setRequiresSelection(boolean requiresSelection)
    {
        _requiresSelection = requiresSelection;
    }

    public Integer getRequiresSelectionMinCount()
    {
        return _requiresSelectionMinCount;
    }

    public void setRequiresSelectionMinCount(Integer requiresSelectionMinCount)
    {
        _requiresSelectionMinCount = requiresSelectionMinCount;
    }

    public Integer getRequiresSelectionMaxCount()
    {
        return _requiresSelectionMaxCount;
    }

    public void setRequiresSelectionMaxCount(Integer requiresSelectionMaxCount)
    {
        _requiresSelectionMaxCount = requiresSelectionMaxCount;
    }

    public Class<? extends Permission> getPermission()
    {
        return _permission;
    }

    public void setPermission(Class<? extends Permission> permission)
    {
        _permission = permission;
    }

    public String getInsertAfter()
    {
        return _insertAfter;
    }

    public void setInsertAfter(String insertAfter)
    {
        _insertAfter = insertAfter;
    }

    public String getInsertBefore()
    {
        return _insertBefore;
    }

    public void setInsertBefore(String insertBefore)
    {
        _insertBefore = insertBefore;
    }

    public Integer getInsertPosition()
    {
        return _insertPosition;
    }

    public void setInsertPosition(Integer insertPosition)
    {
        _insertPosition = insertPosition;
    }

    private String processURL(RenderContext ctx, String url)
    {
        // Basic fixup, since users won't always encode spaces when defining custom button bars via metadata XML
        if (-1 != url.indexOf(' '))
            url = StringUtils.replace(url, " ", "%20");

        StringExpression urlExpr = StringExpressionFactory.createURL(url);
        if (urlExpr != null)
        {
            if (urlExpr instanceof DetailsURL)
                ((DetailsURL) urlExpr).setContainerContext(ctx.getContainer());
            url = urlExpr.eval(ctx);
        }
        return url;
    }

    private void processURLs(RenderContext ctx, NavTree tree)
    {
        if (tree.getHref() != null && tree.getHref().length() > 0)
            tree.setHref(processURL(ctx, tree.getHref()));
        for (NavTree child : tree.getChildren())
            processURLs(ctx, child);
    }

    private String getWrappedOnClick(RenderContext ctx, String originalOnClick)
    {
        if (originalOnClick == null)
            return null;

        StringBuilder onClickWrapper = new StringBuilder();
        onClickWrapper.append("var dataRegionName = ").append(PageFlowUtil.jsString(ctx.getCurrentRegion().getName())).append("; ");
        onClickWrapper.append("var dataRegion = LABKEY.DataRegions[dataRegionName]; ");
        onClickWrapper.append(originalOnClick);
        return onClickWrapper.toString();
    }

    public DisplayElement createButton(RenderContext ctx, List<DisplayElement> originalButtons)
    {
        if (null != _menuItems)
        {
            MenuButton btn = new MenuButton(_text);
            if (getIconCls() != null)
                btn.setIconCls(getIconCls());

            for (NavTree item : _menuItems)
            {
                NavTree toAdd = new NavTree(item);
                wrapOnClick(ctx, toAdd);
                processURLs(ctx, toAdd);
                btn.addMenuItem(toAdd);
            }
            btn.setRequiresSelection(_requiresSelection, _requiresSelectionMinCount, _requiresSelectionMaxCount);
            btn.setDisplayPermission(_permission);
            return btn;
        }
        else
        {
            // An ActionButton must have a valid URL, even if that URL doesn't go anywhere (as in the case of
            // a button with only an onClick handler.
            String url = null==_url ? "#" : processURL(ctx,_url);
            ActionButton btn = new ActionButton(_text, StringExpressionFactory.create(url, true));
            if (getIconCls() != null)
                btn.setIconCls(getIconCls());

            btn.setDisplayPermission(_permission);
            if (null != _onClick)
                btn.setScript(getWrappedOnClick(ctx, _onClick), false);
            if (_action != null)
                btn.setActionType(_action);
            btn.setRequiresSelection(_requiresSelection, _requiresSelectionMinCount, _requiresSelectionMaxCount);
            return btn;
        }
    }

    /** Recursively set the onClick wrapper script for the whole tree */
    private void wrapOnClick(RenderContext ctx, NavTree tree)
    {
        if (tree.getScript() != null)
            tree.setScript(getWrappedOnClick(ctx, tree.getScript()));
        for (NavTree child : tree.getChildren())
        {
            wrapOnClick(ctx, child);
        }
    }

    public UserDefinedButtonConfig clone()
    {
        UserDefinedButtonConfig ret = new UserDefinedButtonConfig();
        ret.setText(_text);
        ret.setUrl(_url);
        ret.setIconCls(_iconCls);
        ret.setOnClick(_onClick);
        ret.setAction(_action);
        ret.setRequiresSelection(_requiresSelection);
        ret.setPermission(_permission);
        ret.setInsertAfter(_insertAfter);
        ret.setInsertBefore(_insertBefore);
        ret.setInsertPosition(_insertPosition);

        if (_menuItems != null)
        {
            List<NavTree> items = new ArrayList<>();
            for (NavTree item : _menuItems)
            {
                items.add(new NavTree(item));
            }
            ret.setMenuItems(items);
        }

        return ret;
    }
}
