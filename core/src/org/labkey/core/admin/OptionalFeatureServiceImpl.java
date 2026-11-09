/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.core.admin;

import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertySchema;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureFlag;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.settings.WriteableAppProps;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class OptionalFeatureServiceImpl implements OptionalFeatureService
{
    private final Set<OptionalFeatureFlag> _optionalFlags = new ConcurrentSkipListSet<>();
    private final Map<String, List<OptionalFeatureListener>> _listeners = new ConcurrentHashMap<>();
    // Checked per column and per exported cell, and the underlying property lookup is comparatively costly
    private final BlockingCache<String, Boolean> _enabled = CacheManager.getBlockingCache(String.class, CacheManager.UNLIMITED, CacheManager.DAY, "Optional feature flags",
        (feature, _) -> WriteableAppProps.isOptionalFeatureEnabledUncached(feature));

    public OptionalFeatureServiceImpl()
    {
    }

    @Override
    public void addFeatureFlag(OptionalFeatureFlag optionalFeatureFlag)
    {
        _optionalFlags.add(optionalFeatureFlag);
    }

    @Override
    public Collection<OptionalFeatureFlag> getOptionalFeatureFlags()
    {
        return Collections.unmodifiableSet(_optionalFlags);
    }

    @Override
    public Collection<OptionalFeatureFlag> getOptionalFeatureFlags(FeatureType type)
    {
        return _optionalFlags.stream()
            .filter(flag -> flag.getType() == type)
            .toList();
    }

    @Override
    public void addFeatureListener(String feature, OptionalFeatureListener listener)
    {
        _listeners.computeIfAbsent(feature, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public boolean isFeatureEnabled(String feature)
    {
        return _enabled.get(feature);
    }

    @Override
    public void removeFeatureListener(String feature, OptionalFeatureListener listener)
    {
        if (_listeners.containsKey(feature))
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
        // Clear again at commit or rollback; a read inside an enclosing transaction would otherwise re-cache a stale value
        PropertySchema.getInstance().getSchema().getScope().addCommitTask(() -> _enabled.remove(feature),
            DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT, DbScope.CommitTaskOption.POSTROLLBACK);
    }

    private void setFeatureEnabled(String feature, boolean enabled, WriteableAppProps props)
    {
        props.setFeatureEnabled(feature, enabled);

        if (_listeners.containsKey(feature))
        {
            for (OptionalFeatureListener listener : _listeners.get(feature))
            {
                listener.featureChanged(feature, enabled);
            }
        }
    }
}
