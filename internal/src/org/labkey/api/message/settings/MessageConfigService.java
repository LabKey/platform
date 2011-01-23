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
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 18, 2011
 * Time: 2:51:40 PM
 */
public class MessageConfigService
{
    public static MessageConfigService.I getInstance()
    {
        MessageConfigService.I impl = ServiceRegistry.get().getService(MessageConfigService.I.class);
        assert (impl != null);

        return impl;
    }

    public interface I
    {
        public void savePreference(User currentUser, Container c, User projectUser, ConfigTypeProvider provider, int preference) throws Exception;

        public UserPreference getPreference(Container c, User user, ConfigTypeProvider provider) throws Exception;
        public UserPreference[] getPreference(Container c, User user) throws Exception;

        public NotificationOption getOption(int optionId) throws Exception;
        public NotificationOption[] getOptions(ConfigTypeProvider provider) throws Exception;
        public NotificationOption[] getOptions() throws Exception;

        public void registerConfigType(ConfigTypeProvider provider);
        public ConfigTypeProvider[] getConfigTypes();

        /**
         * returns a type provider by its unique type identifier
         */
        public ConfigTypeProvider getConfigType(String type);
    }

    /**
     * Defines an interface that various notification types (announcements, files, issues) can implement
     * to allow centralized configuration of settings.
     */
    public interface ConfigTypeProvider
    {
        public void savePreference(User currentUser, Container c, User projectUser, int preference) throws Exception;
        public UserPreference getPreference(Container c, User user) throws Exception;

        public NotificationOption getOption(int optionId) throws Exception;
        public NotificationOption[] getOptions() throws Exception;

        /**
         * Uniquely identifies the provider type
         */
        public String getType();

        /**
         * Specifies a name that can be displayed in the UI
         */
        public String getName();

        public HttpView createConfigPanel(ViewContext context, PanelInfo info) throws Exception;

        public void validateCommand(ViewContext context, Errors errors);

        /**
         *
         * @param context
         * @param errors
         * @return
         * @throws Exception
         */
        public boolean handlePost(ViewContext context, BindException errors) throws Exception;
    }

    /**
     * Defines a preference setting for a user
     */
    public interface UserPreference
    {
        public String getLastModifiedByName();
        public Integer getLastModifiedBy();
        public String getEmail();
        public String getContainer();
        public String getEmailOption();
        public String getFirstName();
        public String getLastName();
        public String getDisplayName();
        public Integer getEmailFormatId();
        public Integer getEmailOptionId();
        public int getUserId();
        public int getPageTypeId();
        public boolean isProjectMember();
        public User getUser();
        public String getType();
    }

    /**
     * Defines an option
     */
    public interface NotificationOption
    {
        public String getEmailOption();
        public int getEmailOptionId();
        public String getType();
    }

    public interface PanelInfo
    {
        public ActionURL getReturnUrl();
        public String getDataRegionSelectionKey();
    }
}
