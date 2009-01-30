/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.view.NavTree;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;

/*
* User: Mark Igra
* Date: Dec 31, 2008
* Time: 3:35:12 PM
*/
public class AppBar extends NavTree
{
    public AppBar(String title, NavTree... buttons)
    {
        this(title, Arrays.asList(buttons));
    }

    public AppBar(String title, List<NavTree> buttons)
    {
        super(title);
        addChildren(buttons);
    }

    public String getAppTitle()
    {
        return getKey();
    }

    public NavTree[] getButtons()
    {
        return getChildren();
    }

    /**
     * Merges an existing NavTree into the app bar.
     * If the NavTree has items that are in the AppBar those are "selected" in the AppBar
     * This is basically a way to integrate navTrail-based code with appBar.
     * If this works, all actions will be appBar-based
     * @param crumbTrail
     */
    public List<NavTree> fixCrumbTrail(List<NavTree> crumbTrail)
    {
        List<NavTree> fixed = new ArrayList<NavTree>();
        if (null == crumbTrail || crumbTrail.size() <= 1)
            return (List<NavTree>) Collections.EMPTY_LIST;
        else
        {
            NavTree selected = null;
            for (NavTree crumb : crumbTrail)
            {
                for (NavTree button : getButtons())
                    if (button.getValue().equalsIgnoreCase(crumb.getValue()))
                        selected = button;
            }
            if (null != selected)
                selected.setSelected(true);

            return crumbTrail.subList(crumbTrail.size() - 1, crumbTrail.size());
        }
    }
}
