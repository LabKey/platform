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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

/**
 * User: klum
 * Date: Jan 18, 2011
 * Time: 2:51:40 PM
 */
public interface MessageConfigService
{
    static MessageConfigService get()
    {
        MessageConfigService impl = ServiceRegistry.get().getService(MessageConfigService.class);
        assert (impl != null);

        return impl;
    }

    void savePreference(User currentUser, Container c, User projectUser, ConfigTypeProvider provider, int preference, String srcIdentifier);

    UserPreference getPreference(Container c, User user, ConfigTypeProvider provider, String srcIdentifier);

    /**
     * Returns preference settings for all users who have read access to the specified container
     * for the config type requested.
     */
    UserPreference[] getPreferences(Container c, ConfigTypeProvider provider);

    NotificationOption getOption(int optionId);

    NotificationOption[] getOptions(ConfigTypeProvider provider);

    void registerConfigType(ConfigTypeProvider provider);

    ConfigTypeProvider[] getConfigTypes();

    /**
     * returns a type provider by its unique type identifier
     */
    ConfigTypeProvider getConfigType(String type);

    /**
     * Defines an interface that various notification types (announcements, files, issues) can implement
     * to allow centralized configuration of settings.
     */
    interface ConfigTypeProvider
    {
        void savePreference(User currentUser, Container c, User projectUser, int preference, String srcIdentifier);
        UserPreference getPreference(Container c, User user, String srcIdentifier);

        /**
         * Returns preference settings for all users who have read access to the specified container.
         */
        UserPreference[] getPreferences(Container c);

        NotificationOption getOption(int optionId);
        NotificationOption[] getOptions();

        /**
         * Uniquely identifies the provider type
         */
        String getType();

        /**
         * Specifies a name that can be displayed in the UI
         */
        String getName();

        EmailConfigForm createConfigForm(ViewContext context, PanelInfo info) throws Exception;

        void validateCommand(ViewContext context, Errors errors);

        /**
         *
         * @param context
         * @param errors
         * @return
         * @throws Exception
         */
        boolean handlePost(ViewContext context, BindException errors) throws Exception;
    }

    /**
     * Define interface for folder administration of default and bulk user settings
     */
    interface EmailConfigForm
    {
        int getDefaultEmailOption();

        void setDefaultEmailOption(int defaultEmailOption);

        int getIndividualEmailOption();

        void setIndividualEmailOption(int individualEmailOption);

        String getDataRegionSelectionKey();

        void setDataRegionSelectionKey(String dataRegionSelectionKey);

        String getType();

        void setType(String type);

        ActionURL getSetDefaultPrefURL();

        void setSetDefaultPrefURL(ActionURL setDefaultPrefURL);

        ConfigTypeProvider getProvider();

        void setReturnUrl(String returnUrl);

        String getReturnUrl();
    }



    /**
     * Defines a preference setting for a user
     */
    interface UserPreference
    {
        User getUser();
        Integer getEmailOptionId();
        void setEmailOptionId(Integer id);
        String getSrcIdentifier();
    }

    /**
     * Defines an option
     */
    interface NotificationOption
    {
        String getEmailOption();
        int getEmailOptionId();
        String getType();
    }

    interface PanelInfo
    {
        ActionURL getReturnUrl();
        String getDataRegionSelectionKey();
    }
}
