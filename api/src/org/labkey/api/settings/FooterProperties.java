/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

public class FooterProperties implements TemplateProperties
{
    private final String FOOTER_CONFIGS = "FooterProperties";
    private final String SHOW_FOOTER_PROPERTY_NAME = "ShowFooter";
    private final String FOOTER_MODULE_PROPERTY_NAME = "FooterModule";
    private final String FILE_NAME = "_footer";
    private final String PROPERTY_DISPLAY_TYPE = "Footer";

    private Container _container;

    public FooterProperties(Container container)
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
        return FOOTER_CONFIGS;
    }

    @Override
    public String getDisplayPropertyName()
    {
        return SHOW_FOOTER_PROPERTY_NAME;
    }

    @Override
    public String getModulePropertyName()
    {
        return FOOTER_MODULE_PROPERTY_NAME;
    }

    @Override
    public String getFileName()
    {
        return FILE_NAME;
    }

    @Override
    public String getShowByDefault() { return "TRUE";}

    @Override
    public String getPropertyDisplayType()
    {
        return PROPERTY_DISPLAY_TYPE;
    }
}
