/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.labkey.api.data.PropertyManager;

import java.util.Map;

public class FooterProperties
{

    public static final String FOOTER_CONFIGS = "FooterProperties";
    public static final String SHOW_FOOTER_PROPERTY_NAME = "ShowFooter";
    public static final String FOOTER_MODULE_PROPERTY_NAME = "FooterModule";

    public static boolean isShowFooter() {
        String showFooter = "TRUE"; // default is to show the footer
        Map<String, String> map = PropertyManager.getProperties(FOOTER_CONFIGS);
        if (!map.isEmpty())
        {
            showFooter = map.get(SHOW_FOOTER_PROPERTY_NAME);
        }
        return !"FALSE".equalsIgnoreCase(showFooter);
    }

    public static void setShowFooter(boolean isShowFooter) {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(FOOTER_CONFIGS, true);
        map.put(SHOW_FOOTER_PROPERTY_NAME, (isShowFooter) ? "TRUE" : "FALSE");
        map.save();
    }

    public static String getFooterModule() {
        Map<String, String> map = PropertyManager.getProperties(FOOTER_CONFIGS);
        return map.get(FOOTER_MODULE_PROPERTY_NAME);
    }

    public static void setFooterModule(String footerModule) {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(FOOTER_CONFIGS, true);
        map.put(FOOTER_MODULE_PROPERTY_NAME, footerModule);
        map.save();
    }
 }
