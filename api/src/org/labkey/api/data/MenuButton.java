/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;

import java.io.IOException;
import java.io.Writer;

/**
 * A button that responds to a user click by popping up a drop-down menu.
 *
 * User: jeckels
 * Date: Nov 15, 2007
 */
public class MenuButton extends ActionButton
{
    protected final PopupMenu popupMenu;

    public MenuButton(String caption)
    {
        this(caption, null);
    }

    public MenuButton(String caption, String menuId)
    {
        super(caption, DataRegion.MODE_GRID, ActionButton.Action.LINK);
        NavTree navTree = new NavTree(caption);
        popupMenu = new PopupMenu(navTree, PopupMenu.Align.LEFT, PopupMenu.ButtonStyle.MENUBUTTON);
        if (menuId != null)
        {
            navTree.setId(menuId);
        }
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        popupMenu.renderMenuButton(ctx, out, _requiresSelection, this);

        if (!BooleanUtils.toBoolean((String)ctx.get(getCaption() + "MenuRendered")))
        {
            ctx.put(getCaption() + "MenuRendered", "true");
            popupMenu.renderMenuScript(out);
        }

    }

    public void addSeparator()
    {
        popupMenu.getNavTree().addSeparator();
    }

    public NavTree addMenuItem(String caption, ActionURL url)
    {
        return addMenuItem(caption, url.toString());
    }

    public NavTree addMenuItem(String caption, String url)
    {
        return addMenuItem(caption, url, null);
    }

    public NavTree addMenuItem(String caption, String url, String onClickScript)
    {
        return addMenuItem(caption, url, onClickScript, false);
    }

    public NavTree addMenuItem(String caption, String url, @Nullable String onClickScript, boolean checked)
    {
        return addMenuItem(caption, url, onClickScript, checked, false);
    }

    public NavTree addMenuItem(String caption, boolean checked, boolean disabled)
    {
        return addMenuItem(caption, null, null, checked, disabled);
    }

    protected NavTree addMenuItem(String caption, String url, @Nullable String onClickScript, boolean checked, boolean disabled)
    {
        NavTree menuItem = new NavTree(caption, url);
        menuItem.setScript(onClickScript);
        menuItem.setSelected(checked);
        menuItem.setDisabled(disabled);

        addMenuItem(menuItem);
        return menuItem;
    }

    public void addMenuItem(NavTree item)
    {
        popupMenu.getNavTree().addChild(item);
    }

    @Override
    public void setCaption(String caption)
    {
        super.setCaption(caption);
        popupMenu.getNavTree().setText(caption);
    }

    public PopupMenu getPopupMenu()
    {
        return popupMenu;
    }

    public NavTree getNavTree()
    {
        return popupMenu.getNavTree();
    }
}
