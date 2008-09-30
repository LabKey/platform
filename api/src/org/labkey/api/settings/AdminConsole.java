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
package org.labkey.api.settings;

import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * User: adam
 * Date: Jul 23, 2008
 * Time: 1:57:13 PM
 */
public class AdminConsole
{
    public static enum SettingsLinkType {Configuration, Management, Diagnostics}

    private static final Map<SettingsLinkType, Collection<AdminLink>> _links = new HashMap<SettingsLinkType, Collection<AdminLink>>();

    static
    {
        for (SettingsLinkType type : SettingsLinkType.values())
            _links.put(type, new LinkedList<AdminLink>());
    }

    public static void addLink(SettingsLinkType type, AdminLink link)
    {
        synchronized (_links)
        {
            _links.get(type).add(link);
        }
    }

    public static void addLink(SettingsLinkType type, String text, ActionURL url)
    {
        addLink(type, new AdminLink(text, url));
    }

    public static Collection<AdminLink> getLinks(SettingsLinkType type)
    {
        synchronized (_links)
        {
            return new LinkedList<AdminLink>(_links.get(type));
        }
    }

    public static class AdminLink
    {
        private final String _text;
        private final ActionURL _url;

        public AdminLink(String text, ActionURL url)
        {
            _text = text;
            _url = url;
        }

        public String getText()
        {
            return _text;
        }

        public ActionURL getUrl()
        {
            return _url;
        }
    }
}
