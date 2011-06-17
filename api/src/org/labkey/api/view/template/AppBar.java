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

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
* User: Mark Igra
* Date: Dec 31, 2008
* Time: 3:35:12 PM
*/
public class AppBar extends NavTree
{
    private String _pageTitle;

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
        fixCrumbTrail(navTrail, null);
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

    /**
     * Merges an existing NavTrail into the app bar, allows us to highlight the appBar based on existing NavTree code
     * Some tab in the app bar must always be selected.
     * Select the last tab with a matching name or link is on the navTrail
     * If the name or url of a tab is the last thing on the navTrail, don't show a page title (just folder title)
     * Otherwise, if the NavTrail is more than length 1, use the last thing on the navTrail as the page title
     * If nothing on the NavTrail matches, set the last tab to selected
     * @param crumbTrail
     */
    public List<NavTree> fixCrumbTrail(List<NavTree> crumbTrail, ActionURL actionURL)
    {
        NavTree[] buttons = getButtons();
        boolean hideTitle = false;

        NavTree selected = getSelected();
        if (null == selected && null != actionURL) //First try to match actionURL
        {
            for (NavTree button : buttons)
                if (button.getValue().equals(actionURL.toString()))
                {
                    selected = button;
                    hideTitle = true;
                    break;
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
            return new ArrayList<NavTree>(0);
        }
        else if (crumbTrail.size() >= 1)
        {
            setPageTitle(crumbTrail.get(crumbTrail.size() - 1).getKey());
            return crumbTrail.subList(crumbTrail.size() -1, crumbTrail.size());
        }
        else
            return crumbTrail;
    }
}
