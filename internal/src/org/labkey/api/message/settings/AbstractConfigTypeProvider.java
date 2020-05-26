/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.message.settings.MessageConfigService.ConfigTypeProvider;
import org.labkey.api.message.settings.MessageConfigService.EmailConfigForm;
import org.labkey.api.message.settings.MessageConfigService.NotificationOption;
import org.labkey.api.message.settings.MessageConfigService.PanelInfo;
import org.labkey.api.message.settings.MessageConfigService.UserPreference;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Collection;

/**
 * User: klum
 * Date: Jan 19, 2011
 * Time: 5:54:06 PM
 */
public abstract class AbstractConfigTypeProvider implements ConfigTypeProvider
{
    @Override
    public void savePreference(User currentUser, Container c, User projectUser, int preference, String srcIdentifier)
    {
        MessageConfigService.get().savePreference(currentUser, c, projectUser, this, preference, srcIdentifier);
    }

    @Override
    public UserPreference getPreference(Container c, User user, String srcIdentifier)
    {
        return MessageConfigService.get().getPreference(c, user, this, srcIdentifier);
    }

    @Override
    public Collection<? extends UserPreference> getPreferences(Container c)
    {
        return MessageConfigService.get().getPreferences(c, this);
    }

    @Override
    public Collection<? extends NotificationOption> getOptions()
    {
        return MessageConfigService.get().getOptions(this);
    }

    @Override
    public EmailConfigForm createConfigForm(ViewContext context, PanelInfo info)
    {
        EmailConfigFormImpl form = new EmailConfigFormImpl();
        form.setType(getType());

        form.setDefaultEmailOption(getDefaultEmailOption(context.getContainer()));
        form.setSetDefaultPrefURL(getSetDefaultPrefURL(context.getContainer()));

        form.setDataRegionSelectionKey(info.getDataRegionSelectionKey());
        form.setReturnUrl(info.getReturnUrl().getLocalURIString());

        return form;
    }

    @Override
    abstract public String getType();

    abstract protected int getDefaultEmailOption(Container c);

    abstract protected ActionURL getSetDefaultPrefURL(Container c);

    public static class EmailConfigFormImpl extends ReturnUrlForm implements EmailConfigForm
    {
        private int _defaultEmailOption;
        private int _individualEmailOption;
        private String _dataRegionSelectionKey;
        private String _type;
        private ActionURL _setDefaultPrefURL;

        @Override
        public int getDefaultEmailOption()
        {
            return _defaultEmailOption;
        }

        @Override
        public void setDefaultEmailOption(int defaultEmailOption)
        {
            _defaultEmailOption = defaultEmailOption;
        }

        @Override
        public int getIndividualEmailOption()
        {
            return _individualEmailOption;
        }

        @Override
        public void setIndividualEmailOption(int individualEmailOption)
        {
            _individualEmailOption = individualEmailOption;
        }

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        @Override
        public String getType()
        {
            return _type;
        }

        @Override
        public void setType(String type)
        {
            _type = type;
        }

        @Override
        public ActionURL getSetDefaultPrefURL()
        {
            return _setDefaultPrefURL;
        }

        @Override
        public void setSetDefaultPrefURL(ActionURL setDefaultPrefURL)
        {
            _setDefaultPrefURL = setDefaultPrefURL;
        }

        @Override
        public ConfigTypeProvider getProvider()
        {
            return MessageConfigService.get().getConfigType(_type);
        }
    }
}
