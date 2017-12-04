/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * A set of buttons that are displayed as a unit. Typically, but not always,
 * attached to a {@link org.labkey.api.data.DataRegion}
 */
public class ButtonBar extends DisplayElement
{
    /** Dictates how the ButtonBar is styled when it's rendered */
    public enum Style
    {
        toolbar,
        separateButtons
    }

    private List<DisplayElement> _elementList = new ArrayList<>();
    private List<String> _missingOriginalCaptions = new ArrayList<>();
    private Style _style = Style.toolbar;
    // It's possible to have multiple button bar configs, as in the case of a tableinfo-level config
    // that's partially overridden by a
    private List<ButtonBarConfig> _configs = null;
    private boolean _alwaysShowRecordSelectors = false;

    /**
     * These button bars are unlikely to be useful for new code. They assume that a specific action name
     * exists within the same controller. Instead, new code should generally use the TableInfo's update/insert/delete
     * URLs to build up button bars.
     *
     * In the future, we could create a factory that creates button bars based on a TableInfo, and call it from
     * various places that are building custom UI.
     */
    public static ButtonBar BUTTON_BAR_GRID = new ButtonBar();
    public static ButtonBar BUTTON_BAR_DETAILS = new ButtonBar();
    public static ButtonBar BUTTON_BAR_INSERT = new ButtonBar();
    public static ButtonBar BUTTON_BAR_UPDATE = new ButtonBar();
    public static ButtonBar BUTTON_BAR_EMPTY = new ButtonBar();

    static
    {
        BUTTON_BAR_GRID.add(ActionButton.BUTTON_DELETE).add(ActionButton.BUTTON_SHOW_INSERT);
        BUTTON_BAR_GRID.lock();
        MemTracker.getInstance().remove(BUTTON_BAR_GRID);

        BUTTON_BAR_DETAILS.getList().add(ActionButton.BUTTON_SHOW_UPDATE);
        BUTTON_BAR_DETAILS.getList().add(ActionButton.BUTTON_SHOW_GRID);
        BUTTON_BAR_DETAILS.setStyle(Style.separateButtons);
        BUTTON_BAR_DETAILS.lock();
        MemTracker.getInstance().remove(BUTTON_BAR_DETAILS);

        BUTTON_BAR_INSERT.getList().add(ActionButton.BUTTON_DO_INSERT);
        BUTTON_BAR_INSERT.setStyle(Style.separateButtons);
        BUTTON_BAR_INSERT.lock();
        MemTracker.getInstance().remove(BUTTON_BAR_INSERT);

        BUTTON_BAR_UPDATE.getList().add(ActionButton.BUTTON_DO_UPDATE);
        BUTTON_BAR_UPDATE.setStyle(Style.separateButtons);
        BUTTON_BAR_UPDATE.lock();
        MemTracker.getInstance().remove(BUTTON_BAR_UPDATE);

        BUTTON_BAR_EMPTY.lock();
        MemTracker.getInstance().remove(BUTTON_BAR_EMPTY);
    }

    public ButtonBar() {}

    // cloning constructor
    public ButtonBar(ButtonBar original)
    {
        _elementList = new ArrayList<>(original.getList());
        _style = original.getStyle();
    }

    public List<DisplayElement> getList()
    {
        return _elementList;
    }

    public List<String> getMissingOriginalCaptions()
    {
        return _missingOriginalCaptions;
    }

    public void lock()
    {
        super.lock();
        _elementList = Collections.unmodifiableList(_elementList);
    }

    public ButtonBar addAll(@Nullable Collection<? extends DisplayElement> elements)
    {
        if (elements != null)
        {
            for (DisplayElement de : elements)
                add(de);
        }
        return this;
    }

    public ButtonBar add(@Nullable DisplayElement... elements)
    {
        if (null != elements)
        {
            for (DisplayElement de : elements)
            {
                if (de != null)
                    _elementList.add(de);
            }
        }
        return this;
    }

    public ButtonBar add(int index, @Nullable DisplayElement element)
    {
        if (element != null)
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
        out.write("<div");
        if (getStyle() == Style.toolbar)
            out.write(" class=\"labkey-button-bar\"");
        else if (getStyle() == Style.separateButtons)
            out.write(" class=\"labkey-button-bar-separate\"");
        out.write(">");
        for (DisplayElement el : getList())
        {
            if (ctx.getMode() != DataRegion.MODE_NONE && (ctx.getMode() & el.getDisplayModes()) == 0)
                continue;

            // This is redundant with shouldRender check in ActionButton.render, but we don't want to output <td></td> if button is not visible
            if (el.shouldRender(ctx))
            {
                el.render(ctx, out);
            }
        }
        out.write("</div>");
    }

    /**
     * Checks if any of the configured buttons with permission to render has requires selection.
     * This method should be called after the ButtonBar has been configured by {@link #setConfigs(RenderContext, List)}.
     *
     * @param ctx RenderContext used to check if button should render (has permission)
     * @return true if any of the configured buttons requires selection.
     */
    public boolean hasRequiresSelectionButton(RenderContext ctx)
    {
        if (!shouldRender(ctx))
            return false;

        for (DisplayElement el : getList())
        {
            if (ctx.getMode() != DataRegion.MODE_NONE && (ctx.getMode() & el.getDisplayModes()) == 0)
                continue;

            if (el.shouldRender(ctx) && el instanceof ActionButton && ((ActionButton)el).hasRequiresSelection())
                return true;
        }

        return false;
    }

    @Nullable
    public DataRegion.ButtonBarPosition getConfiguredPosition()
    {
        if (_configs != null && _configs.size() > 0)
            return _configs.get(_configs.size() - 1).getPosition();
        return null;
    }

    public Style getStyle()
    {
        return _style;
    }

    public void setStyle(Style style)
    {
        if (_locked)
            throw new IllegalStateException("Button bar is locked.");
        _style = style;
    }

    /**
     * Apply the list of ButtonBarConfigs to this ButtonBar and populate the list of buttons.
     * After the configs are set the ButtonBar is locked and no new buttons may be added.
     * We may remove this restriction in later releases.
     */
    public void setConfigs(RenderContext ctx, List<ButtonBarConfig> configs)
    {
        checkLocked();
        _configs = configs;
        if (_configs != null && !_configs.isEmpty())
        {
            for (ButtonBarConfig config : _configs)
                applyConfig(ctx, config);
        }
        lock();
    }

    private void applyConfig(RenderContext ctx, ButtonBarConfig config)
    {
        if (config == null)
            return;

        if (config.isAlwaysShowRecordSelectors())
            _alwaysShowRecordSelectors = true;

        List<DisplayElement> originalButtons = _elementList;
        _elementList = new ArrayList<>();

        List<Pair<ButtonConfig, DisplayElement>> mergedItems = new ArrayList<>();
        if (config.getItems() != null)
        {
            for (ButtonConfig item : config.getItems())
            {
                DisplayElement elem = item.createButton(ctx, originalButtons);
                if (null == elem)
                {
                    if (item instanceof BuiltInButtonConfig && !((BuiltInButtonConfig) item).isSuppressWarning())
                    {
                        _missingOriginalCaptions.add(((BuiltInButtonConfig) item).getOriginalCaption());
                    }
                    continue;
                }

                if (item.getInsertAfter() != null || item.getInsertBefore() != null || item.getInsertPosition() != null)
                    mergedItems.add(new Pair<>(item, elem));
                else
                    add(elem);
            }
        }

        if (config.isIncludeStandardButtons())
        {
            //include all buttons in the originalButtons List that
            //are not already in the new element list or the merged list
            //match based on button caption
            Set<String> newCaptions = new HashSet<>();
            for (DisplayElement elem : _elementList)
            {
                newCaptions.add(elem.getCaption());
            }

            for (Pair<ButtonConfig, DisplayElement> pair : mergedItems)
            {
                newCaptions.add(pair.second.getCaption());
            }

            for (DisplayElement elem : originalButtons)
            {
                if (!newCaptions.contains(elem.getCaption()) && !config.getHiddenStandardButtons().contains(elem.getCaption()))
                    _elementList.add(elem);
            }
        }

        for (Pair<ButtonConfig, DisplayElement> pair : mergedItems)
        {
            boolean added = false;
            ButtonConfig item = pair.first;
            DisplayElement elem = pair.second;

            if (item.getInsertBefore() != null || item.getInsertAfter() != null)
            {
                boolean before = item.getInsertBefore() != null;
                String target = item.getInsertBefore() != null ? item.getInsertBefore() : item.getInsertAfter();

                for (int i = 0; i < _elementList.size(); i++)
                {
                    DisplayElement existing = _elementList.get(i);
                    String caption = existing.getCaption();
                    if (caption != null && caption.equalsIgnoreCase(target))
                    {
                        add(before ? i : i + 1, elem);
                        added = true;
                        break;
                    }
                }

            }
            else if (item.getInsertPosition() != null)
            {
                if (item.getInsertPosition() == -1)
                    add(elem);
                else
                    add(item.getInsertPosition(), elem);
                added = true;
            }

            // Just add to end if we didn't find an element to insert before or after.
            if (!added)
                add(elem);
        }

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
