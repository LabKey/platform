/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.MemTracker;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Collection;

public class ButtonBar extends DisplayElement
{
    private List<DisplayElement> _elementList = new ArrayList<DisplayElement>();

    public static ButtonBar BUTTON_BAR_GRID = new ButtonBar();
    public static ButtonBar BUTTON_BAR_DETAILS = new ButtonBar();
    public static ButtonBar BUTTON_BAR_INSERT = new ButtonBar();
    public static ButtonBar BUTTON_BAR_UPDATE = new ButtonBar();
    public static ButtonBar BUTTON_BAR_EMPTY = new ButtonBar();

    static
    {
        BUTTON_BAR_GRID.add(ActionButton.BUTTON_DELETE).add(ActionButton.BUTTON_SHOW_INSERT);
        BUTTON_BAR_GRID.lock();
        assert MemTracker.remove(BUTTON_BAR_GRID);

        BUTTON_BAR_DETAILS.getList().add(ActionButton.BUTTON_SHOW_UPDATE);
        BUTTON_BAR_DETAILS.getList().add(ActionButton.BUTTON_SHOW_GRID);
        BUTTON_BAR_DETAILS.lock();
        assert MemTracker.remove(BUTTON_BAR_DETAILS);

        BUTTON_BAR_INSERT.getList().add(ActionButton.BUTTON_DO_INSERT);
        BUTTON_BAR_INSERT.lock();
        assert MemTracker.remove(BUTTON_BAR_INSERT);

        BUTTON_BAR_UPDATE.getList().add(ActionButton.BUTTON_DO_UPDATE);
        BUTTON_BAR_UPDATE.lock();
        assert MemTracker.remove(BUTTON_BAR_UPDATE);

        BUTTON_BAR_EMPTY.lock();
        assert MemTracker.remove(BUTTON_BAR_EMPTY);
    }

    public ButtonBar() {}

    // cloning constructor
    public ButtonBar(ButtonBar original)
    {
        _elementList = new ArrayList<DisplayElement>(original.getList());
    }

    public List<DisplayElement> getList()
    {
        return _elementList;
    }

    public void lock()
    {
        super.lock();
        _elementList = Collections.unmodifiableList(_elementList);
    }

    public ButtonBar addAll(Collection<? extends DisplayElement> elements)
    {
        _elementList.addAll(elements);
        return this;
    }

    public ButtonBar add(DisplayElement element)
    {
        _elementList.add(element);
        return this;
    }

    public ButtonBar add(int index, DisplayElement element)
    {
        _elementList.add(index, element);
        return this;
    }

    public boolean shouldRender(RenderContext ctx)
    {
        return getList().size() > 0 && super.shouldRender(ctx);
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (!shouldRender(ctx))
            return;

        // Write out an empty column so that we can easily write a display element that wraps to the next line
        // by closing the current cell, closing the table, opening a new table, and opening an empty cell
        out.write("<div class=\"labkey-button-bar\">");
        for (DisplayElement el : getList())
        {
            if (ctx.getMode() != 0 && (ctx.getMode() & el.getDisplayModes()) == 0)
                continue;

            // This is rendundant with shouldRender check in ActionButton.render, but we don't want to output <td></td> if button is not visible
            if (el.shouldRender(ctx))
            {
                out.write("<span>");
                el.render(ctx, out);
                out.write("</span>");
            }
        }
        out.write("</div>");
    }
} 
