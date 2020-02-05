/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.Constants;
import org.labkey.api.gwt.client.ui.property.FormatItem;
import org.labkey.api.view.NavTree;

import java.util.Formatter;
import java.util.Map;

/**
 * User: Tamra Myers
 * Date: Feb 15, 2007
 * Time: 1:10:09 PM
 */
public class HelpTopic
{
    private static final String TARGET_NAME = "labkeyHelp"; // LabKey help should always appear in the same tab/window
    private static final String HELP_VERSION = Formats.f1.format(Constants.getPreviousReleaseVersion());
    private static final String HELP_LINK_PREFIX = "https://www.labkey.org/Documentation/" + HELP_VERSION + "/wiki-page.view?name=";

    public static final HelpTopic DEFAULT_HELP_TOPIC = new HelpTopic("default");

    private final String _topic;

    public HelpTopic(@NotNull String topic)
    {
        _topic = topic;
    }

    @Override
    public String toString()
    {
        return getHelpTopicHref();
    }

    public static String getHelpLinkPrefix()
    {
        return HELP_LINK_PREFIX;
    }

    public static String getJdkJavaDocLinkPrefix()
    {
        return FormatItem.JDK_JAVADOC_BASE_URL;
    }

    public String getHelpTopicHref()
    {
        return HELP_LINK_PREFIX + _topic;
    }

    // Create a simple link (just an <a> tag with plain mixed case text, no graphics) to the help topic, displaying
    // the provided text, using the standard target, etc. Use in cases where LabKey standard link style doesn't fit in.
    public HtmlString getSimpleLinkHtml(String displayText)
    {
        return PageFlowUtil.link(displayText).href(getHelpTopicHref()).target(TARGET_NAME).clearClasses().getHtmlString();
    }

    // TODO: Use this in places where it makes sense (search results page, etc.)
    // Create a standard LabKey style link (all caps + arrow right) to the help topic, displaying the provided text, using the standard target, etc.
    public HtmlString getLinkHtml(String displayText)
    {
        return PageFlowUtil.link(displayText).href(getHelpTopicHref()).target(TARGET_NAME).getHtmlString();
    }

    // Create a NavTree for a menu item that links to the help topic, displaying the provided text, using the standard target, etc.
    public NavTree getNavTree(String displayText)
    {
        NavTree tree = new NavTree(displayText, getHelpTopicHref());
        tree.setTarget(HelpTopic.TARGET_NAME);

        return tree;
    }

    /**
     * @return a link to this class' JavaDoc in the current LabKey-supported Oracle JDK
     */
    public static String getJDKJavaDocLink(Class c)
    {
        return FormatItem.JDK_JAVADOC_BASE_URL + c.getName().replace(".", "/").replace("$", ".") + ".html";
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testJavaDocLinkGeneration()
        {
            assertEquals(FormatItem.JDK_JAVADOC_BASE_URL + "java/util/Formatter.html", getJDKJavaDocLink(Formatter.class));
            assertEquals(FormatItem.JDK_JAVADOC_BASE_URL + "java/util/Map.Entry.html", getJDKJavaDocLink(Map.Entry.class));
        }
    }
}
