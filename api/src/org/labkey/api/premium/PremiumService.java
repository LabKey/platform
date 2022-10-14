/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.premium;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

public interface PremiumService
{
    static @NotNull PremiumService get()
    {
        // Return default service if premium module not registered
        PremiumService service = ServiceRegistry.get().getService(PremiumService.class);
        if (null == service)
            service = new DefaultPremiumService();
        return service;
    }

    boolean isEnabled();

    boolean isDisableFileUploadSupported();

    boolean isFileUploadDisabled();

    boolean isFileWatcherSupported();

    default CommonsMultipartResolver getMultipartResolver(ViewBackgroundInfo info)
    {
        return new CommonsMultipartResolver();
    }

    void registerAntiVirusProvider(AntiVirusProvider avp);

    static void setInstance(PremiumService instance)
    {
        ServiceRegistry.get().registerService(PremiumService.class, instance);
    }

    boolean isRemoteREnabled();

    @Nullable
    @Deprecated // moved to ModuleEditorService
    default ActionURL getUpdateModuleURL(String module)
    {
        return null;
    }

    @Nullable
    @Deprecated // moved to ModuleEditorService
    default ActionURL getCreateModuleURL()
    {
        return null;
    }

    @Nullable
    @Deprecated // moved to ModuleEditorService
    default ActionURL getDeleteModuleURL(String module)
    {
        return null;
    }

    interface AntiVirusProvider
    {
        @NotNull String getId();             // something unique e.g. className
        @NotNull String getDescription();    // e.g. ClamAV Daemon
        @Nullable ActionURL getConfigurationURL();
        @NotNull AntiVirusService getService();
    }

    class DefaultPremiumService implements PremiumService
    {
        @Override
        public boolean isEnabled()
        {
            return false;
        }

        @Override
        public boolean isDisableFileUploadSupported()
        {
            return false;
        }

        @Override
        public boolean isFileUploadDisabled()
        {
            return false;
        }

        @Override
        public boolean isFileWatcherSupported()
        {
            return false;
        }

        @Override
        public void registerAntiVirusProvider(AntiVirusProvider avp) {}

        @Override
        public boolean isRemoteREnabled()
        {
            return false;
        }
    }
}
