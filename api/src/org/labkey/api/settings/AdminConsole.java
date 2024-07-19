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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.settings.OptionalFeatureService.FeatureType;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Set<OptionalFeatureFlag> _optionalFlags = new ConcurrentSkipListSet<>();
    private static final Map<String, Product>  _products = new ConcurrentHashMap<>();

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
        addOptionalFeatureFlag(new OptionalFeatureFlag(flag, title, description, requiresRestart, false, FeatureType.Experimental));
    }

    public static void addOptionalFeatureFlag(OptionalFeatureFlag optionalFeatureFlag)
    {
        _optionalFlags.add(optionalFeatureFlag);
    }

    // Return all optional features, regardless of type
    public static Collection<OptionalFeatureFlag> getOptionalFeatureFlags()
    {
        return Collections.unmodifiableSet(_optionalFlags);
    }

    // Return all optional features having the specified type
    public static Collection<OptionalFeatureFlag> getOptionalFeatureFlags(FeatureType type)
    {
        return _optionalFlags.stream()
            .filter(flag -> flag.getType() == type)
            .toList();
    }

    public static class OptionalFeatureFlag implements Comparable<OptionalFeatureFlag>, StartupProperty
    {
        private final String _flag;
        private final String _title;
        private final String _description;
        private final boolean _requiresRestart;
        private final boolean _hidden;
        private final FeatureType _type;

        public OptionalFeatureFlag(String flag, String title, String description, boolean requiresRestart, boolean hidden, FeatureType type)
        {
            _flag = flag;
            _title = title;
            _description = description;
            _requiresRestart = requiresRestart;
            _hidden = hidden;
            _type = type;
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
        public int compareTo(@NotNull AdminConsole.OptionalFeatureFlag o)
        {
            return getTitle().compareToIgnoreCase(o.getTitle());
        }

        public boolean isEnabled()
        {
            return AppProps.getInstance().isOptionalFeatureEnabled(getFlag());
        }

        public boolean isHidden()
        {
            return _hidden;
        }

        public FeatureType getType()
        {
            return _type;
        }

        // StartupProperty implementation

        @NotNull
        @Override
        public String getPropertyName()
        {
            return getFlag();
        }
    }

    public static void addProduct(Product product)
    {
        _products.put(product.getKey(), product);
    }

    public static Set<String> getProductFeatureSet()
    {
        Set<String> productFeatures = new HashSet<>();
        String product = new ProductConfiguration().getCurrentProduct();
        if (_products.containsKey(product))
            productFeatures.addAll(_products.get(product).getFeatureFlags());
        return productFeatures;
    }

    public static Collection<Product> getProducts()
    {
        return getProducts(false);
    }

    public static Collection<Product> getProducts(boolean sorted)
    {
        if (!sorted)
            return _products.values();

        List<Product> orderedProducts = new ArrayList<>(_products.values());
        orderedProducts.sort((a, b) -> {
            if (a == b) return 0;
            if (null == a) return -1;
            if (null == b) return 1;
            return a.getOrderNum() - b.getOrderNum();
        });
        return orderedProducts;
    }

    public static boolean isProductFeatureEnabled(ProductFeature feature)
    {
        return getProductFeatureSet().contains(feature.toString());
    }

    public static abstract class Product implements Comparable<Product>
    {
        public abstract Integer getOrderNum();

        public abstract String getName();

        public abstract String getKey();

        public abstract boolean isEnabled();

        public abstract @NotNull List<String> getFeatureFlags();

        @Override
        public int compareTo(@NotNull Product o)
        {
            return getName().compareToIgnoreCase(o.getName());
        }
    }
}
