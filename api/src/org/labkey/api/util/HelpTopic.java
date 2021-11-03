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
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.Constants;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.Link.LinkBuilder;
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
    // Point at the Java documentation that matches the currently running JVM
    private static final String JDK_JAVADOC_BASE_URL = ModuleLoader.getInstance().getJavaVersion().getJavaDocBaseURL();
    private static final String TARGET_NAME = "labkeyHelp"; // LabKey help should always appear in the same tab/window
    private static final String DOCUMENTATION_FOLDER_NAME = Constants.getDocumentationVersion();
    private static final String HELP_LINK_PREFIX = "https://www.labkey.org/Documentation/" + DOCUMENTATION_FOLDER_NAME + "/wiki-page.view?name=";

    public static final HelpTopic DEFAULT_HELP_TOPIC = new HelpTopic("default");

    private final String _topic;
    private final String _fragment;

    public String getTopic()
    {
        return _topic;
    }

    public HelpTopic(@NotNull String topic)
    {
        this(topic.contains("#") ? topic.split("#")[0] : topic, topic.contains("#") ? topic.split("#")[1] : null);
    }

    public HelpTopic(@NotNull String topic, @Nullable String fragment)
    {
        _topic = topic;
        _fragment = fragment;
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
        return JDK_JAVADOC_BASE_URL;
    }

    public String getHelpTopicHref()
    {
        return getHelpTopicHref(Referrer.inPage);
    }

    public enum Referrer
    {
        /** The LabKey Documentation link under the user menu in the main page header */
        docMenu,
        /** Help links under the Developer header menu */
        devMenu,
        /** Links in the main page or its tooltips */
        inPage
    }

    public String getHelpTopicHref(@NotNull Referrer referrer)
    {
        return HELP_LINK_PREFIX + _topic + "&referrer=" + referrer + (_fragment == null ? "" : ("#" + _fragment));
    }

    // Create a simple link (just an <a> tag with plain mixed case text, no graphics) to the help topic, displaying
    // the provided text, using the standard target, etc. Use in cases where LabKey standard link style doesn't fit in.
    public HtmlString getSimpleLinkHtml(String displayText)
    {
        return getLink(displayText).clearClasses().getHtmlString();
    }

    // TODO: Use this in places where it makes sense (search results page, etc.)
    // Create a standard LabKey style link (all caps + arrow right) to the help topic, displaying the provided text, using the standard target, etc.
    public HtmlString getLinkHtml(String displayText)
    {
        return getLink(displayText).getHtmlString();
    }

    private LinkBuilder getLink(String displayText)
    {
        return PageFlowUtil.link(displayText).href(getHelpTopicHref()).target(TARGET_NAME).rel("noopener noreferrer");
    }

    // Create a NavTree for a menu item that links to the help topic, displaying the provided text, using the standard target, etc.
    public NavTree getNavTree(String displayText)
    {
        NavTree tree = new NavTree(displayText, getHelpTopicHref(Referrer.docMenu));
        tree.setTarget(HelpTopic.TARGET_NAME);

        return tree;
    }

    /**
     * @return a link to this class' JavaDoc for the currently running JDK version
     */
    public static String getJDKJavaDocLink(Class c)
    {
        return JDK_JAVADOC_BASE_URL + c.getName().replace(".", "/").replace("$", ".") + ".html";
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testJavaDocLinkGeneration()
        {
            assertEquals(JDK_JAVADOC_BASE_URL + "java/util/Formatter.html", getJDKJavaDocLink(Formatter.class));
            assertEquals(JDK_JAVADOC_BASE_URL + "java/util/Map.Entry.html", getJDKJavaDocLink(Map.Entry.class));
        }
    }
}
