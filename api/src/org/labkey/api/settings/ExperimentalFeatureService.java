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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.labkey.api.settings.AppPropsImpl.EXPERIMENTAL_FEATURE_PREFIX;

/**
 * Manages the list of experimental features that can be enabled or disabled within a given deployment,
 * and their current state. Generally speaking, they are not considered production-ready to be turned on, and will
 * be disabled by default.
 */
public interface ExperimentalFeatureService
{
    static ExperimentalFeatureService get()
    {
        return ServiceRegistry.get().getService(ExperimentalFeatureService.class);
    }

    static void setInstance(ExperimentalFeatureService impl)
    {
        ServiceRegistry.get().registerService(ExperimentalFeatureService.class, impl);
    }

    void addFeatureListener(String feature, ExperimentFeatureListener listener);

    boolean isFeatureEnabled(String feature);

    void removeFeatureListener(String feature, ExperimentFeatureListener listener);

    void setFeatureEnabled(String feature, boolean enabled, User user);

    interface ExperimentFeatureListener
    {
        void featureChanged(String feature, boolean enabled);
    }

    class ExperimentalFeatureServiceImpl implements ExperimentalFeatureService
    {
        private Map<String, List<ExperimentFeatureListener>> _listeners;

        public ExperimentalFeatureServiceImpl()
        {
        }

        public void addFeatureListener(String feature, ExperimentFeatureListener listener)
        {
            if (_listeners == null)
                _listeners = Collections.synchronizedMap(new HashMap<>());

            if (!_listeners.containsKey(feature))
                _listeners.put(feature, new CopyOnWriteArrayList<>());

            _listeners.get(feature).add(listener);
        }

        public boolean isFeatureEnabled(String feature)
        {
            return AppProps.getInstance().isExperimentalFeatureEnabled(feature);
        }

        public void removeFeatureListener(String feature, ExperimentFeatureListener listener)
        {
            if (_listeners != null && _listeners.containsKey(feature))
            {
                _listeners.get(feature).remove(listener);
            }
        }

        public void setFeatureEnabled(String feature, boolean enabled, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            setFeatureEnabled(feature, enabled, props);
            props.save(user);
        }

        private void setFeatureEnabled(String feature, boolean enabled, WriteableAppProps props)
        {
            props.storeBooleanValue(EXPERIMENTAL_FEATURE_PREFIX + feature, enabled);

            if (_listeners != null && _listeners.containsKey(feature))
            {
                for (ExperimentFeatureListener listener : _listeners.get(feature))
                {
                    listener.featureChanged(feature, enabled);
                }
            }
        }

    }
}
