/*
 * Copyright (c) 2005-2013 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;

/**
 * User: brittp
 * Date: Sep 21, 2005
 * Time: 4:26:39 PM
 */
public class NavTrailConfig extends PageConfig
{
    private ArrayList<NavTree> _extraChildren = new ArrayList<>();

    public NavTrailConfig(ViewContext context)
    {
        super(context.getActionURL().getController());
    }

    public NavTrailConfig()
    {
    }

    public NavTrailConfig clearExtraChildren()
    {
        _extraChildren.clear();
        return this;
    }

    public NavTrailConfig setExtraChildren(NavTree... extraChildren)
    {
        clearExtraChildren();
        if (extraChildren != null)
            addExtraChildren(extraChildren);
        return this;
    }

    public NavTrailConfig addExtraChildren(NavTree... extraChildren)
    {
        for (NavTree n : extraChildren)
            add(n);
        return this;
    }

    public NavTrailConfig add(NavTree n)
    {
        _extraChildren.add(n);
        return this;
    }

    public NavTree[] getExtraChildren()
    {
        return _extraChildren.toArray(new NavTree[_extraChildren.size()]);
    }


    // overrides to down-cast return type
    public NavTrailConfig setHelpTopic(HelpTopic topic)
    {
        return (NavTrailConfig)super.setHelpTopic(topic);
    }

    public NavTrailConfig setTitle(String title)
    {
        return (NavTrailConfig)super.setTitle(title);
    }
}
