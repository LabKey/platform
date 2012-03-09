/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.NavTree;

/**
 * User: Tamra Myers
 * Date: Feb 15, 2007
 * Time: 1:10:09 PM
 */
public class HelpTopic
{
    public static String TARGET_NAME = "labkeyHelp"; // LabKey help should always appear in the same tab/window
    private static String HELP_VERSION = null;

    private String _topic;

    static
    {
        // Get core module version number, truncate to one decimal place, and use as help version
        Module core = ModuleLoader.getInstance().getCoreModule();
        double coreVersion = core.getVersion();
        HELP_VERSION = Formats.f1.format(Math.floor(coreVersion * 10) / 10);
    }

    private static final String HELP_ROOT_URL = "http://help.labkey.org/wiki/home/documentation/" + HELP_VERSION + "/page.view?name=";

    public static final HelpTopic DEFAULT_HELP_TOPIC = new HelpTopic("default");

    public HelpTopic(@NotNull String topic)
    {
        if (topic == null)
            throw new IllegalArgumentException("Topic cannot be null");
        
        _topic = topic;
    }

    @Override
    public String toString()
    {
        return getHelpTopicHref();
    }

    public String getHelpTopicHref()
    {
        return HELP_ROOT_URL + _topic;
    }

    // Create a standard <a> tag that links to the help topic, displays the provided text, uses the standard target, etc.
    public String getLinkHtml(String displayText)
    {
        StringBuilder html = new StringBuilder();
        html.append("<a href=\"");
        html.append(PageFlowUtil.filter(getHelpTopicHref()));
        html.append("\" target=\"");
        html.append(TARGET_NAME);
        html.append("\">");
        html.append(PageFlowUtil.filter(displayText));
        html.append("</a>");

        return html.toString();
    }

    // Get create a NavTree for a menu item that to the help topic, displays the provided text, uses the standard target, etc.
    public NavTree getNavTree(String displayText)
    {
        NavTree tree = new NavTree(displayText, getHelpTopicHref());
        tree.setTarget(HelpTopic.TARGET_NAME);

        return tree;
    }
}
