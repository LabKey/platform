/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.apache.commons.lang.ObjectUtils;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
* User: Mark Igra
* Date: Dec 31, 2008
* Time: 3:35:12 PM
*/
public class AppBar extends NavTree
{
    private String _pageTitle;
    private List<NavTree> _navTrail;

    public AppBar(String folderTitle, NavTree... buttons)
    {
        this(folderTitle, Arrays.asList(buttons));
    }

    public AppBar(String folderTitle, List<NavTree> buttons)
    {
        super(folderTitle);
        addChildren(buttons);
    }

    public AppBar(List<NavTree> navTrail, List<NavTree> buttons)
    {
        this(navTrail.size() > 0 ? navTrail.get(0).getKey(): "No Page Title...", buttons);
        this._navTrail = fixCrumbTrail(navTrail, null);
    }

    public String getFolderTitle()
    {
        return getKey();
    }

    public NavTree[] getButtons()
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

    public void setNavTrail(List<NavTree> navTrail)
    {
        _navTrail = setNavTrail(navTrail, null);
    }

    public List<NavTree> setNavTrail(List<NavTree> navTrail, ActionURL actionURL)
    {
        _navTrail = fixCrumbTrail(navTrail, actionURL);
        return _navTrail;
    }

    /**
     * Merges an existing NavTrail into the app bar, allows us to highlight the appBar based on existing NavTree code
     * If the name or url of a tab is the last thing on the navTrail, don't show a page title (just folder title)
     * Otherwise, if the NavTrail is more than length 1, use the last thing on the navTrail as the page title
     */
    private List<NavTree> fixCrumbTrail(List<NavTree> crumbTrail, ActionURL actionURL)
    {
        NavTree[] buttons = getButtons();
        boolean hideTitle = false;

        NavTree selected = getSelected();
        if (null == selected && null != actionURL) //First try to match actionURL
        {
            for (NavTree button : buttons)
            {
                if (button.getValue().equals(actionURL.toString()))
                {
                    selected = button;
                    hideTitle = true;
                    break;
                }
            }
        }

        if (null == selected)
            for (NavTree crumb : crumbTrail)
            {
                for (NavTree button : buttons)
                    if (button.getValue().equalsIgnoreCase(crumb.getValue()) || button.getKey().equalsIgnoreCase(crumb.getKey()))
                        selected = button;
            }

        if (null != selected)
            selected.setSelected(true);
//        else
//            buttons[buttons.length -1].setSelected(true);

        if (hideTitle)
        {
            setPageTitle(null);
            return Collections.emptyList();
        }
        else if (crumbTrail.size() >= 1)
        {
            // Last item is page title, strip it off the crumb trail
            setPageTitle(crumbTrail.get(crumbTrail.size() - 1).getKey());
            crumbTrail = crumbTrail.subList(0, crumbTrail.size() - 1);

            // First item in crumb trail is usually the home page. See if it matches one of the tabs
            if (!crumbTrail.isEmpty())
            {
                String firstLink = crumbTrail.get(0).getValue();
                for (NavTree button : buttons)
                {
                    if (ObjectUtils.equals(button.getValue(), firstLink) || (actionURL != null && ObjectUtils.equals(actionURL.getLocalURIString(), firstLink)))
                    {
                        // Exclude the duplicate item
                        return crumbTrail.subList(1, crumbTrail.size());
                    }
                }
            }
            else
                return Collections.emptyList();
        }

        return crumbTrail;
    }
}
