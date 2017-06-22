/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.announcements.config;

import org.labkey.announcements.model.MessageConfigManager;
import org.labkey.api.data.Container;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * User: klum
 * Date: Jan 19, 2011
 * Time: 4:36:05 PM
 */
public class MessageConfigServiceImpl implements MessageConfigService
{
    private final Map<String, MessageConfigService.ConfigTypeProvider> _providers = new ConcurrentSkipListMap<>();

    @Override
    public void savePreference(User currentUser, Container c, User projectUser, MessageConfigService.ConfigTypeProvider provider, int preference, String srcIdentifier)
    {
        MessageConfigManager.saveEmailPreference(currentUser, c, projectUser, provider.getType(), preference, srcIdentifier);
    }

    @Override
    public MessageConfigService.UserPreference getPreference(Container c, User user, MessageConfigService.ConfigTypeProvider provider, String srcIdentifier)
    {
        return MessageConfigManager.getUserEmailPrefRecord(c, user, provider.getType(), srcIdentifier);
    }

    @Override
    public MessageConfigService.UserPreference[] getPreferences(Container c, MessageConfigService.ConfigTypeProvider provider)
    {
        return MessageConfigManager.getUserEmailPrefs(c, provider.getType());
    }

    @Override
    public MessageConfigService.NotificationOption getOption(int optionId)
    {
        return MessageConfigManager.getEmailOption(optionId);
    }

    @Override
    public MessageConfigService.NotificationOption[] getOptions(MessageConfigService.ConfigTypeProvider provider)
    {
        return MessageConfigManager.getEmailOptions(provider.getType());
    }

    public void registerConfigType(MessageConfigService.ConfigTypeProvider provider)
    {
        String key = provider.getType();
        ConfigTypeProvider previous = _providers.putIfAbsent(key, provider);

        if (null != previous)
            throw new IllegalArgumentException("ConfigService provider " + key + " has already been registered");
    }

    public MessageConfigService.ConfigTypeProvider[] getConfigTypes()
    {
        return _providers.values().toArray(new MessageConfigService.ConfigTypeProvider[_providers.size()]);
    }

    public MessageConfigService.ConfigTypeProvider getConfigType(String identifier)
    {
        return _providers.get(identifier);
    }
}
