/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.search.model;

import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
 * User: adam
 * Date: Apr 20, 2010
 * Time: 7:01:16 PM
 */
public class ExternalIndexManager
{
    private static final String CATEGORY = "ExternalSearchIndex";
    private static final String PATH = "path";
    private static final String ANALYZER = "analyzer";
    private static final String DESCRIPTION = "description";

    public static ExternalIndexProperties get()
    {
        Map<String, String> map = PropertyManager.getProperties(CATEGORY);
        final String path = map.get(PATH);
        final String analyzer = map.get(ANALYZER);
        final String description = map.get(DESCRIPTION);

        return new ExternalIndexProperties() {
            @Override
            public String getExternalIndexPath()
            {
                return path;
            }

            @Override
            public String getAnalyzer()
            {
                return analyzer;
            }

            @Override
            public String getExternalIndexDescription()
            {
                return description;
            }

            @Override
            public boolean hasProperties()
            {
                return null != path && null != analyzer;
            }
        };
    }

    public static void save(ExternalIndexProperties props)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        map.put(PATH, props.getExternalIndexPath());
        map.put(ANALYZER, props.getAnalyzer());
        map.put(DESCRIPTION, props.getExternalIndexDescription());
        PropertyManager.saveProperties(map);
    }

    public static void clear()
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        map.clear();
        PropertyManager.saveProperties(map);
    }
}
