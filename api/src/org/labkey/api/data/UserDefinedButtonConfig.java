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

import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.NavTree;

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
    private ActionButton.Action _action;
    private boolean _requiresSelection = false;
    /** Permission that a user must have in order to see the button */
    private Class<? extends Permission> _permission;
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

    public Class<? extends Permission> getPermission()
    {
        return _permission;
    }

    public void setPermission(Class<? extends Permission> permission)
    {
        _permission = permission;
    }

    private String processURL(RenderContext ctx, String url)
    {
        StringExpression urlExpr = StringExpressionFactory.createURL(url);
        if (urlExpr != null)
        {
            if (urlExpr instanceof DetailsURL)
                ((DetailsURL) urlExpr).setContainer(ctx.getContainer());
            url = urlExpr.eval(ctx);
        }
        return url;
    }

    private void processURLs(RenderContext ctx, NavTree tree)
    {
        if (tree.getValue() != null && tree.getValue().length() > 0)
            tree.setValue(processURL(ctx, tree.getValue()));
        for (NavTree child : tree.getChildList())
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
            for (NavTree item : _menuItems)
            {
                NavTree toAdd = new NavTree(item);
                toAdd.setScript(getWrappedOnClick(ctx, toAdd.getScript()));
                processURLs(ctx, toAdd);
                btn.addMenuItem(toAdd);
            }
            btn.setRequiresSelection(_requiresSelection);
            btn.setDisplayPermission(_permission);
            return btn;
        }
        else
        {
            // An ActionButton must have a valid URL, even if that URL doesn't go anywhere (as in the case of
            // a button with only an onClick handler.
            ActionButton btn = new ActionButton();
            btn.setCaption(_text);
            btn.setDisplayPermission(_permission);
            btn.setURL(_url != null ? processURL(ctx, _url) : "#");
            if (null != _onClick)
                btn.setScript(getWrappedOnClick(ctx, _onClick), false);
            if (_action != null)
                btn.setActionType(_action);
            btn.setRequiresSelection(_requiresSelection);
            return btn;
        }
    }
}
