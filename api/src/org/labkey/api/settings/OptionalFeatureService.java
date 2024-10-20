/*
 * Copyright (c) 2017-2018 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.labkey.api.settings.AppPropsImpl.OPTIONAL_FEATURE_PREFIX;

/**
 * Manages the optional features that can be enabled or disabled within a given deployment, and their current state.
 * Optional features may be experimental (not ready for production use), deprecated (supported at the moment, but will
 * be removed soon), or simply optional (not widely used, perhaps client-specific). Optional features are off by default.
 */
public interface OptionalFeatureService
{
    static OptionalFeatureService get()
    {
        return ServiceRegistry.get().getService(OptionalFeatureService.class);
    }

    static void setInstance(OptionalFeatureService impl)
    {
        ServiceRegistry.get().registerService(OptionalFeatureService.class, impl);
    }

    void addFeatureListener(String feature, OptionalFeatureListener listener);

    boolean isFeatureEnabled(String feature);

    void removeFeatureListener(String feature, OptionalFeatureListener listener);

    void setFeatureEnabled(String feature, boolean enabled, User user);

    interface OptionalFeatureListener
    {
        void featureChanged(String feature, boolean enabled);
    }

    // FeatureType is an optional feature flag property that determines the admin page on which the feature appears.
    // The property is used at run-time registration only; it is not persisted. All optional properties are persisted
    // and retrieved the same way, and can be populated using the "ExperimentalFeature" startup property prefix. This
    // means features can be switched to a different FeatureType at any time.
    enum FeatureType
    {
        Deprecated
        {
            @Override
            public HtmlString getAdminGuidance()
            {
                return HtmlString.unsafe(
                    """
                    <strong>WARNING</strong>:
                    Deprecated features will be removed very soon, most likely for the next major release.
                    If you enable one of these features you should also create a plan to stop relying on it.
                    """
                );
            }
        },
        Experimental
        {
            @Override
            public HtmlString getAdminGuidance()
            {
                return HtmlString.unsafe(
                    """
                    <strong>WARNING</strong>:
                    Experimental features may change, break, or disappear at any time.
                    We make absolutely no guarantee about what will happen if you turn on any experimental feature.
                    """
                );
            }
        },
        Optional
        {
            @Override
            public HtmlString getAdminGuidance()
            {
                return HtmlString.unsafe(
                    """
                    Optional features are not typically used; discuss with your account manager before enabling any
                    optional feature.
                    """
                );
            }
        };

        public abstract HtmlString getAdminGuidance();
    }

    class OptionalFeatureServiceImpl implements OptionalFeatureService
    {
        private Map<String, List<OptionalFeatureListener>> _listeners;

        public OptionalFeatureServiceImpl()
        {
        }

        @Override
        public void addFeatureListener(String feature, OptionalFeatureListener listener)
        {
            if (_listeners == null)
                _listeners = Collections.synchronizedMap(new HashMap<>());

            if (!_listeners.containsKey(feature))
                _listeners.put(feature, new CopyOnWriteArrayList<>());

            _listeners.get(feature).add(listener);
        }

        @Override
        public boolean isFeatureEnabled(String feature)
        {
            return AppProps.getInstance().isOptionalFeatureEnabled(feature);
        }

        @Override
        public void removeFeatureListener(String feature, OptionalFeatureListener listener)
        {
            if (_listeners != null && _listeners.containsKey(feature))
            {
                _listeners.get(feature).remove(listener);
            }
        }

        @Override
        public void setFeatureEnabled(String feature, boolean enabled, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            setFeatureEnabled(feature, enabled, props);
            props.save(user);
        }

        private void setFeatureEnabled(String feature, boolean enabled, WriteableAppProps props)
        {
            props.storeBooleanValue(OPTIONAL_FEATURE_PREFIX + feature, enabled);

            if (_listeners != null && _listeners.containsKey(feature))
            {
                for (OptionalFeatureListener listener : _listeners.get(feature))
                {
                    listener.featureChanged(feature, enabled);
                }
            }
        }
    }
}
