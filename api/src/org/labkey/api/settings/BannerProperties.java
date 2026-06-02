/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.api.settings;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

public class BannerProperties implements TemplateProperties
{
    private final Container _container;

    public BannerProperties(Container container)
    {
        _container = container;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
    public String getDisplayConfigs()
    {
        return "BannerProperties";
    }

    @Override
    public String getDisplayPropertyName()
    {
        return "ShowBanner";
    }

    @Override
    public String getModulePropertyName()
    {
        return "BannerModule";
    }

    @Override
    public String getFileName()
    {
        return "_banner";
    }

    @Override
    public String getPropertyDisplayType()
    {
        return "Banner";
    }

    @Override
    public String getDefaultModule()
    {
        return null;
    }

    @Override
    public TemplateProperties getRootProperties()
    {
        return new BannerProperties(ContainerManager.getRoot());
    }
}
