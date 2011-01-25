/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.message.settings;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 19, 2011
 * Time: 5:54:06 PM
 */
public abstract class AbstractConfigTypeProvider implements MessageConfigService.ConfigTypeProvider
{
    @Override
    public void savePreference(User currentUser, Container c, User projectUser, int preference) throws Exception
    {
        MessageConfigService.getInstance().savePreference(currentUser, c, projectUser, this, preference);
    }

    @Override
    public MessageConfigService.UserPreference getPreference(Container c, User user) throws Exception
    {
        return MessageConfigService.getInstance().getPreference(c, user, this);
    }

    @Override
    public MessageConfigService.UserPreference[] getPreferences(Container c) throws Exception
    {
        return MessageConfigService.getInstance().getPreferences(c, this);
    }

    @Override
    public MessageConfigService.NotificationOption getOption(int optionId) throws Exception
    {
        return MessageConfigService.getInstance().getOption(optionId);
    }

    @Override
    public MessageConfigService.NotificationOption[] getOptions() throws Exception
    {
        return MessageConfigService.getInstance().getOptions(this);
    }
}
