/*
 * Copyright (c) 2017 LabKey Corporation
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

/**
 * Created by marty on 7/5/2017.
 */
public class HeaderProperties implements TemplateProperties
{
    private final String HEADER_CONFIGS = "HeaderProperties";
    private final String SHOW_HEADER_PROPERTY_NAME = "ShowHeader";
    private final String HEADER_MODULE_PROPERTY_NAME = "HeaderModule";
    private final String FILE_NAME = "_header";

    public String getDisplayConfigs()
    {
        return HEADER_CONFIGS;
    }

    public String getDisplayPropertyName()
    {
        return SHOW_HEADER_PROPERTY_NAME;
    }

    public String getModulePropertyName()
    {
        return HEADER_MODULE_PROPERTY_NAME;
    }

    public String getFileName()
    {
        return FILE_NAME;
    }

    public String getShowByDefault() { return "FALSE";}
}
