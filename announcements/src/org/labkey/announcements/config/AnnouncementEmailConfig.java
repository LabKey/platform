/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.announcements.config;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.message.settings.AbstractConfigTypeProvider;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Set;

public class AnnouncementEmailConfig extends AbstractConfigTypeProvider implements MessageConfigService.ConfigTypeProvider 
{
    public static final String TYPE = "messages";

    @Override
    public String getType()
    {
        return TYPE;
    }

    public String getName()
    {
        // appears in the config tab
        return getType();
    }

    @Override
    public HttpView createConfigPanel(ViewContext context, MessageConfigService.PanelInfo info) throws Exception
    {
        EmailConfigForm form = new EmailConfigForm();

        form.setDefaultEmailOption(AnnouncementManager.getDefaultEmailOption(context.getContainer()));
        form.setEmailOptions(AnnouncementManager.getEmailOptions());
        form.setDataRegionSelectionKey(info.getDataRegionSelectionKey());
        form.setReturnUrl(new ReturnURLString(info.getReturnUrl().getLocalURIString()));

        return new JspView<>("/org/labkey/announcements/view/announcementNotifySettings.jsp", form);
    }

    @Override
    public void validateCommand(ViewContext context, Errors errors)
    {
        Set<String> selected = DataRegionSelection.getSelected(context, false);

        if (selected.isEmpty())
            errors.reject(SpringActionController.ERROR_MSG, "There are no users selected for this update.");
    }

    @Override
    public boolean handlePost(ViewContext context, BindException errors) throws Exception
    {
        Object selectedOption = context.get("selectedEmailOption");
        // Only supports container-level subscriptions for this bulk UI
        String srcIdentifier = context.getContainer().getId();

        if (selectedOption != null)
        {
            int newOption = NumberUtils.toInt((String)selectedOption);
            for (String selected : DataRegionSelection.getSelected(context, true))
            {
                User projectUser = UserManager.getUser(Integer.parseInt(selected));
                int currentEmailOption = AnnouncementManager.getUserEmailOption(context.getContainer(), projectUser, srcIdentifier);

                //has this projectUser's option changed? if so, update
                //creating new record in EmailPrefs table if there isn't one, or deleting if set back to folder default
                if (currentEmailOption != newOption)
                {
                    AnnouncementManager.saveEmailPreference(context.getUser(), context.getContainer(), projectUser, newOption, srcIdentifier);
                }
            }
            return true;
        }
        return false;
    }

    public static class EmailConfigForm extends ReturnUrlForm
    {
        int _defaultEmailOption;
        int _individualEmailOption;
        MessageConfigService.NotificationOption[] _emailOptions;
        String _dataRegionSelectionKey;
        String _type;

        public int getDefaultEmailOption()
        {
            return _defaultEmailOption;
        }

        public void setDefaultEmailOption(int defaultEmailOption)
        {
            _defaultEmailOption = defaultEmailOption;
        }

        public MessageConfigService.NotificationOption[] getEmailOptions()
        {
            return _emailOptions;
        }

        public void setEmailOptions(MessageConfigService.NotificationOption[] emailOptions)
        {
            _emailOptions = emailOptions;
        }

        public int getIndividualEmailOption()
        {
            return _individualEmailOption;
        }

        public void setIndividualEmailOption(int individualEmailOption)
        {
            _individualEmailOption = individualEmailOption;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public MessageConfigService.ConfigTypeProvider getProvider()
        {
            return MessageConfigService.getInstance().getConfigType(_type);
        }
    }
}