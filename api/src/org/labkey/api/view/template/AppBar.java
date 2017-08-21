/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.view.template;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
* User: Mark Igra
* Date: Dec 31, 2008
* Time: 3:35:12 PM
*/
public class AppBar extends NavTree
{
    private static final String LAST_TAB_KEY = "LastActiveTab"; 

    private String _pageTitle;
    private List<NavTree> _navTrail;
    private List<NavTree> _subContainerTabs;

    public AppBar(String folderTitle, ActionURL titleURL, NavTree... buttons)
    {
        this(folderTitle, titleURL, Arrays.asList(buttons));
    }

    public AppBar(String folderTitle, ActionURL titleURL, List<NavTree> buttons)
    {
        super(folderTitle, titleURL);
        addChildren(buttons);
    }

    public AppBar(String folderTitle, ActionURL titleURL, List<NavTree> buttons, List<NavTree> subContainerTabs)
    {
        super(folderTitle, titleURL);
        addChildren(buttons);
        setSubContainerTabs(subContainerTabs);
    }

    public String getFolderTitle()
    {
        return getText();
    }

    public @NotNull List<NavTree> getButtons()
    {
        return getChildren();
    }

    public void setPageTitle(String pageTitle)
    {
        _pageTitle = pageTitle;
    }

    public String getPageTitle()
    {
        return _pageTitle;     
    }

    public NavTree getSelected()
    {
        for (NavTree button : getButtons())
            if (button.isSelected())
                return button;

        return null;
    }

    public List<NavTree> getNavTrail()
    {
        return _navTrail;
    }

    public List<NavTree> setNavTrail(List<NavTree> navTrail, ViewContext context)
    {
        if (null == navTrail)
            navTrail = Collections.emptyList();
        _navTrail = fixCrumbTrail(navTrail, context);
        return _navTrail;
    }

    public List<NavTree> getSubContainerTabs()
    {
        return _subContainerTabs;
    }

    public void setSubContainerTabs(List<NavTree> subContainerTabs)
    {
        _subContainerTabs = subContainerTabs;
    }

    /**
     * Merges an existing NavTrail into the app bar, allows us to highlight the appBar based on existing NavTree code
     * If the name or url of a tab is the last thing on the navTrail, don't show a page title (just folder title)
     * Otherwise, if the NavTrail is more than length 1, use the last thing on the navTrail as the page title
     */
    private List<NavTree> fixCrumbTrail(List<NavTree> crumbTrail, ViewContext context)
    {
        ActionURL actionURL = context.getActionURL();
        List<NavTree> buttons = getButtons();
        boolean hideTitle = false;

        NavTree selected = getSelected();
        if (null == selected && null != actionURL) //First try to match actionURL
        {
            for (NavTree button : buttons)
            {
                if (button.getHref().equals(actionURL.toString()))
                {
                    selected = button;
                    hideTitle = true;
                    break;
                }
            }
        }

        if (null == selected)
        {
            for (NavTree crumb : crumbTrail)
            {
                for (NavTree button : buttons)
                    if (button.getHref().equalsIgnoreCase(crumb.getHref()) || button.getText().equalsIgnoreCase(crumb.getText()))
                        selected = button;
            }
        }

        Map<String, String> lastTabs = getLastTabMap(context);
        if (selected == null && !buttons.isEmpty())
        {
            // No tab was recognized as the active tab, so try to figure out which one to select
            String lastTab = lastTabs.get(context.getContainer().getId());
            if (lastTab != null)
            {
                // See if we have a previously active tab that we can select
                for (NavTree button : buttons)
                {
                    if (button.getText().equals(lastTab))
                    {
                        // Found a match
                        selected = button;
                        break;
                    }
                }
            }
            if (selected == null)
            {
                // No matches for previously selected tab, so just choose the first one
                selected = buttons.get(0);
            }
        }
        else if (selected != null)
        {
            // If a tab was recognized as being the active tab for this URL, remember it
            lastTabs.put(context.getContainer().getId(), selected.getText());
        }

        if (null != selected)
            selected.setSelected(true);

        if (hideTitle)
        {
            setPageTitle(null);
            return Collections.emptyList();
        }
        else if (crumbTrail.size() >= 1)
        {
            // Last item is page title, strip it off the crumb trail
            setPageTitle(crumbTrail.get(crumbTrail.size() - 1).getText());

            List<NavTree> result = new ArrayList<>();
            boolean stopLooking = false;

            for (int i = 0; i < crumbTrail.size() - 1; i++)
            {
                String link = crumbTrail.get(i).getHref();
                boolean foundMatch = false;
                if (!stopLooking)
                {
                    // First check the folder title's link
                    if (Objects.equals(getHref(), link))
                    {
                        foundMatch = true;
                    }
                    // Then look at all of the tabs
                    for (NavTree button : buttons)
                    {
                        if (Objects.equals(button.getHref(), link) || (actionURL != null && Objects.equals(actionURL.getLocalURIString(), link)))
                        {
                            foundMatch = true;
                        }
                    }
                }
                if (!foundMatch)
                {
                    stopLooking = true;
                    result.add(crumbTrail.get(i));
                }
            }

            return result;
        }

        return crumbTrail;
    }

    /** @return containerId->most recently active tab name mapping for the current HTTP session */
    private Map<String, String> getLastTabMap(ViewContext context)
    {
        try
        {
            HttpSession session = context.getRequest().getSession(true);
            Map<String, String> lastTabs = (Map<String, String>) session.getAttribute(LAST_TAB_KEY);
            if (lastTabs == null)
            {
                // Need to synchronize since multiple requests may be happening concurrently
                lastTabs = Collections.synchronizedMap(new HashMap<String, String>());
                session.setAttribute(LAST_TAB_KEY, lastTabs);
            }
            return lastTabs;
        }
        catch (IllegalStateException ignored)
        {
            // That's OK, we just won't remember their last tab
            return Collections.emptyMap();
        }
    }
}
