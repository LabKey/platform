package org.labkey.core.admin;

import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureFlag;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.settings.WriteableAppProps;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class OptionalFeatureServiceImpl implements OptionalFeatureService
{
    private final Set<OptionalFeatureFlag> _optionalFlags = new ConcurrentSkipListSet<>();
    private final Map<String, List<OptionalFeatureListener>> _listeners = Collections.synchronizedMap(new HashMap<>());

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
