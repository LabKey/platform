/*
 * Copyright (c) 2011-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.notification;

import org.labkey.api.data.Container;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * User: klum
 * Date: Jan 19, 2011
 * Time: 4:36:05 PM
 */
public class EmailPreferenceConfigServiceImpl implements MessageConfigService
{
    private final Map<String, ConfigTypeProvider> _providers = new ConcurrentSkipListMap<>();

    @Override
    public void savePreference(User currentUser, Container c, User projectUser, ConfigTypeProvider provider, int preference, String srcIdentifier)
    {
        EmailPreferenceConfigManager.saveEmailPreference(currentUser, c, projectUser, provider.getType(), preference, srcIdentifier);
    }

    @Override
    public UserPreference getPreference(Container c, User user, ConfigTypeProvider provider, String srcIdentifier)
    {
        return EmailPreferenceConfigManager.getUserEmailPrefRecord(c, user, provider.getType(), srcIdentifier);
    }

    @Override
    public Collection<? extends UserPreference> getPreferences(Container c, ConfigTypeProvider provider)
    {
        return EmailPreferenceConfigManager.getUserEmailPrefs(c, provider.getType());
    }

    @Override
    public MessageConfigService.NotificationOption getOption(int optionId)
    {
        return EmailPreferenceConfigManager.getEmailOption(optionId);
    }

    @Override
    public Collection<? extends NotificationOption> getOptions(ConfigTypeProvider provider)
    {
        return EmailPreferenceConfigManager.getEmailOptions(provider.getType());
    }

    @Override
    public void registerConfigType(ConfigTypeProvider provider)
    {
        String key = provider.getType();
        ConfigTypeProvider previous = _providers.putIfAbsent(key, provider);

        if (null != previous)
            throw new IllegalArgumentException("ConfigService provider " + key + " has already been registered");
    }

    @Override
    public Collection<ConfigTypeProvider> getConfigTypes()
    {
        return _providers.values();
    }

    @Override
    public ConfigTypeProvider getConfigType(String identifier)
    {
        return null==identifier ? null : _providers.get(identifier);
    }
}
