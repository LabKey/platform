/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * User: adam
 * Date: Jul 23, 2008
 * Time: 1:57:13 PM
 */
public class AdminConsole
{
    public enum SettingsLinkType
    {
        Premium
        {
            @Override
            public String getCaption()
            {
                return "Premium Features";
            }
        },
        Configuration,
        Management,
        Diagnostics;

        public String getCaption()
        {
            return name();
        }
    }

    private static final Map<SettingsLinkType, Collection<AdminLink>> _links = new HashMap<>();
    private static final List<ExperimentalFeatureFlag> _experimentalFlags = new ArrayList<>();

    static
    {
        for (SettingsLinkType type : SettingsLinkType.values())
            _links.put(type, new TreeSet<>());
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
            return new LinkedList<>(_links.get(type));
        }
    }

    public static class AdminLink implements Comparable<AdminLink>
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

        @Override
        public int compareTo(@NotNull AdminLink o)
        {
            return getText().compareToIgnoreCase(o.getText());
        }
    }

    public static void addExperimentalFeatureFlag(String flag, String title, String description, boolean requiresRestart)
    {
        synchronized (_experimentalFlags)
        {
            _experimentalFlags.add(new ExperimentalFeatureFlag(flag, title, description, requiresRestart));
        }
    }

    public static Collection<ExperimentalFeatureFlag> getExperimentalFeatureFlags()
    {
        synchronized (_experimentalFlags)
        {
            return Collections.unmodifiableList(_experimentalFlags);
        }
    }

    public static class ExperimentalFeatureFlag implements Comparable<ExperimentalFeatureFlag>
    {
        private final String _flag;
        private final String _title;
        private final String _description;
        private final boolean _requiresRestart;

        public ExperimentalFeatureFlag(String flag, String title, String description, boolean requiresRestart)
        {
            _flag = flag;
            _title = title;
            _description = description;
            _requiresRestart = requiresRestart;
        }

        public String getFlag()
        {
            return _flag;
        }

        public String getTitle()
        {
            return _title;
        }

        public String getDescription()
        {
            return _description;
        }

        public boolean isRequiresRestart()
        {
            return _requiresRestart;
        }

        @Override
        public int compareTo(@NotNull ExperimentalFeatureFlag o)
        {
            return getFlag().compareToIgnoreCase(o.getFlag());
        }

        public boolean isEnabled()
        {
            return AppProps.getInstance().isExperimentalFeatureEnabled(getFlag());
        }
    }
}
