/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Nov 15, 2007
 */
public class MenuButton extends ActionButton
{
    protected PopupMenu popupMenu;

    public MenuButton(String caption)
    {
        super("MenuButton", caption, DataRegion.MODE_GRID, ActionButton.Action.LINK);
        popupMenu = new PopupMenu(new NavTree(caption), PopupMenu.Align.LEFT, PopupMenu.ButtonStyle.MENUBUTTON);
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        popupMenu.renderMenuButton(out);

        if (!BooleanUtils.toBoolean((String)ctx.get(getCaption() + "MenuRendered")))
        {
            ctx.put(getCaption() + "MenuRendered", "true");
            popupMenu.renderMenuScript(out);
        }

    }

    public void addSeparator()
    {
        popupMenu.getNavTree().addChild(NavTree.MENU_SEPARATOR);
    }

    public void addMenuItem(String caption, ActionURL url)
    {
        addMenuItem(caption, url.toString());
    }

    public NavTree addMenuItem(String caption, String url)
    {
        return addMenuItem(caption, url, null);
    }

    public NavTree addMenuItem(String caption, String url, String onClickScript)
    {
        return addMenuItem(caption, url, onClickScript, false);
    }

    public NavTree addMenuItem(String caption, String url, String onClickScript, boolean checked)
    {
        return addMenuItem(caption, url, onClickScript, checked, false);
    }

    public NavTree addMenuItem(String caption, boolean checked, boolean disabled)
    {
        return addMenuItem(caption, null, null, checked, disabled);
    }

    protected NavTree addMenuItem(String caption, String url, String onClickScript, boolean checked, boolean disabled)
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
}
