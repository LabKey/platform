/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Manages registration for links to be shown in the Admin Console, as well as experimental features that can
 * be enabled/disabled.
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
    private static final Set<ExperimentalFeatureFlag> _experimentalFlags = new ConcurrentSkipListSet<>();
    private static final Set<ProductGroup> _productGroups = new ConcurrentSkipListSet<>();

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
        addLink(type, new AdminLink(text, url, null));
    }

    public static void addLink(SettingsLinkType type, String text, ActionURL url, Class<? extends Permission> requiredPerm)
    {
        addLink(type, new AdminLink(text, url, requiredPerm));
    }

    public static Collection<AdminLink> getLinks(SettingsLinkType type, User user)
    {
        synchronized (_links)
        {
            Container root = ContainerManager.getRoot();
            List<AdminLink> links = new LinkedList<>();
            for (AdminLink link : _links.get(type))
            {
                if (link.getRequiredPerm() == null || root.hasPermission(user, link.getRequiredPerm()))
                    links.add(link);
            }

            return links;
        }
    }

    public static class AdminLink implements Comparable<AdminLink>
    {
        private final String _text;
        private final ActionURL _url;
        // defines a permission class the user must have to see this link in the admin console
        private final Class<? extends Permission> _requiredPerm;

        public AdminLink(String text, ActionURL url, Class<? extends Permission> requiredPerm)
        {
            _text = text;
            _url = url;
            _requiredPerm = requiredPerm;
        }

        public String getText()
        {
            return _text;
        }

        public ActionURL getUrl()
        {
            return _url;
        }

        public Class<? extends Permission> getRequiredPerm()
        {
            return _requiredPerm;
        }

        @Override
        public int compareTo(@NotNull AdminLink o)
        {
            return getText().compareToIgnoreCase(o.getText());
        }
    }

    public static void addExperimentalFeatureFlag(String flag, String title, String description, boolean requiresRestart)
    {
        addExperimentalFeatureFlag(new ExperimentalFeatureFlag(flag, title, description, requiresRestart, false));
    }

    public static void addExperimentalFeatureFlag(ExperimentalFeatureFlag experimentalFeatureFlag)
    {
        _experimentalFlags.add(experimentalFeatureFlag);
    }

    public static Collection<ExperimentalFeatureFlag> getExperimentalFeatureFlags()
    {
        return Collections.unmodifiableSet(_experimentalFlags);
    }

    public static class ExperimentalFeatureFlag implements Comparable<ExperimentalFeatureFlag>, StartupProperty
    {
        private final String _flag;
        private final String _title;
        private final String _description;
        private final boolean _requiresRestart;
        private final boolean _hidden;

        public ExperimentalFeatureFlag(String flag, String title, String description, boolean requiresRestart, boolean hidden)
        {
            _flag = flag;
            _title = title;
            _description = description;
            _requiresRestart = requiresRestart;
            _hidden = hidden;
        }

        public String getFlag()
        {
            return _flag;
        }

        public String getTitle()
        {
            return _title;
        }

        @Override
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
            return getTitle().compareToIgnoreCase(o.getTitle());
        }

        public boolean isEnabled()
        {
            return AppProps.getInstance().isExperimentalFeatureEnabled(getFlag());
        }

        public boolean isHidden()
        {
            return _hidden;
        }

        // StartupProperty implementation

        @NotNull
        @Override
        public String getPropertyName()
        {
            return getFlag();
        }
    }

    public static void addProductGroup(ProductGroup group)
    {
        _productGroups.add(group);
    }

    public static Set<ProductGroup> getProductGroups()
    {
        return Collections.unmodifiableSet(_productGroups);
    }

    public static Set<String> getProductFeatureSet()
    {
        return getProductFeatureSet(null);
    }

    public static Set<String> getProductFeatureSet(@Nullable String groupKey)
    {
        Set<String> productFeatures = new HashSet<>();
        AdminConsole.getProductGroups(groupKey).forEach(group -> {
            group.getProducts().forEach(product -> {
                if (product.isEnabled())
                    productFeatures.addAll(product.getFeatureFlags());
            });
        });
        return productFeatures;
    }

    public static Set<ProductGroup> getProductGroups(@Nullable String groupKey)
    {
        Set<ProductGroup> productGroups = new HashSet<>();
        AdminConsole.getProductGroups().forEach(group -> {
            if (StringUtils.isEmpty(groupKey) || groupKey.equals(group.getKey()))
                productGroups.add(group);
        });
        return productGroups;
    }

    public static boolean isProductFeatureEnabled(ProductFeature feature)
    {
        return isProductFeatureEnabled(feature, null);
    }

    // TODO This is currently (22.12) not in use, but could become useful. We will leave it for the time being.
    public static boolean isProductFeatureEnabled(ProductFeature feature, @Nullable String groupKey)
    {
        return getProductFeatureSet(groupKey).contains(feature.toString());
    }

    public static abstract class ProductGroup implements Comparable<ProductGroup>
    {
        public abstract String getName();

        public abstract String getKey();

        public abstract List<Product> getProducts();

        @Override
        public int compareTo(@NotNull ProductGroup o)
        {
            return getName().compareToIgnoreCase(o.getName());
        }
    }

    public interface Product
    {
        String getName();

        String getKey();

        boolean isEnabled();

        @NotNull List<String> getFeatureFlags();
    }

}
