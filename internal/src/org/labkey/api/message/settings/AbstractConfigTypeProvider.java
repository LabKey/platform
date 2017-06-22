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
package org.labkey.api.message.settings;

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

/**
 * User: klum
 * Date: Jan 19, 2011
 * Time: 5:54:06 PM
 */
public abstract class AbstractConfigTypeProvider implements MessageConfigService.ConfigTypeProvider
{
    @Override
    public void savePreference(User currentUser, Container c, User projectUser, int preference, String srcIdentifier)
    {
        MessageConfigService.get().savePreference(currentUser, c, projectUser, this, preference, srcIdentifier);
    }

    @Override
    public MessageConfigService.UserPreference getPreference(Container c, User user, String srcIdentifier)
    {
        return MessageConfigService.get().getPreference(c, user, this, srcIdentifier);
    }

    @Override
    public MessageConfigService.UserPreference[] getPreferences(Container c)
    {
        return MessageConfigService.get().getPreferences(c, this);
    }

    @Override
    public MessageConfigService.NotificationOption getOption(int optionId)
    {
        return MessageConfigService.get().getOption(optionId);
    }

    @Override
    public MessageConfigService.NotificationOption[] getOptions()
    {
        return MessageConfigService.get().getOptions(this);
    }

    @Override
    public MessageConfigService.EmailConfigForm createConfigForm(ViewContext context, MessageConfigService.PanelInfo info) throws Exception
    {
        MessageConfigService.EmailConfigForm form = new EmailConfigFormImpl();
        form.setType(getType());

        form.setDefaultEmailOption(getDefaultEmailOption(context.getContainer()));
        form.setSetDefaultPrefURL(getSetDefaultPrefURL(context.getContainer()));

        form.setDataRegionSelectionKey(info.getDataRegionSelectionKey());
        form.setReturnUrl(info.getReturnUrl().getLocalURIString());

        return form;
    }

    abstract public String getType();

    abstract protected int getDefaultEmailOption(Container c);

    abstract protected ActionURL getSetDefaultPrefURL(Container c);

    public static class EmailConfigFormImpl extends ReturnUrlForm implements MessageConfigService.EmailConfigForm
    {
        int _defaultEmailOption;
        int _individualEmailOption;
        String _dataRegionSelectionKey;
        String _type;
        ActionURL _setDefaultPrefURL;

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
        public MessageConfigService.ConfigTypeProvider getProvider()
        {
            return MessageConfigService.get().getConfigType(_type);
        }
    }
}
