/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.search.SearchModule;

import java.io.File;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 20, 2010
 * Time: 7:01:16 PM
 */
public class SearchPropertyManager
{
    private static final String CATEGORY = SearchModule.class.getName();
    private static final String EXTERNAL_PATH = "externalPath";
    private static final String EXTERNAL_ANALYZER = "externalAnalyzer";
    private static final String EXTERNAL_DESCRIPTION = "externalDescription";
    private static final String CRAWLER_RUNNING_STATE = "runningState";
    private static final String PRIMARY_INDEX_PATH = "primaryIndexPath";
    private static final String DIRECTORY_TYPE = "directoryType";

    public static ExternalIndexProperties getExternalIndexProperties()
    {
        final Map<String, String> map = PropertyManager.getProperties(CATEGORY);
        final String externalIndexPath = map.get(EXTERNAL_PATH);
        final String externalIndexAnalyzer = map.get(EXTERNAL_ANALYZER);
        final String externalIndexDescription = map.get(EXTERNAL_DESCRIPTION);

        return new ExternalIndexProperties() {
            @Override
            public String getExternalIndexPath()
            {
                return externalIndexPath;
            }

            @Override
            public String getExternalIndexAnalyzer()
            {
                return externalIndexAnalyzer;
            }

            @Override
            public String getExternalIndexDescription()
            {
                return externalIndexDescription;
            }

            @Override
            public boolean hasExternalIndex()
            {
                return null != externalIndexPath && null != externalIndexAnalyzer && null != externalIndexDescription;
            }
        };
    }

    public static void saveExternalIndexProperties(ExternalIndexProperties props)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        map.put(EXTERNAL_PATH, props.getExternalIndexPath());
        map.put(EXTERNAL_ANALYZER, props.getExternalIndexAnalyzer());
        map.put(EXTERNAL_DESCRIPTION, props.getExternalIndexDescription());
        map.save();
    }

    public static void clearExternalIndexProperties()
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        map.remove(EXTERNAL_PATH);
        map.remove(EXTERNAL_ANALYZER);
        map.remove(EXTERNAL_DESCRIPTION);
        map.save();
    }

    public static boolean getCrawlerRunningState()
    {
        String state = getProperty(CRAWLER_RUNNING_STATE);

        if (null != state)
            return "true".equals(state);
        else
            return !AppProps.getInstance().isDevMode();
    }

    public static void setCrawlerRunningState(boolean running)
    {
        setProperty(CRAWLER_RUNNING_STATE, String.valueOf(running));
    }

    public static File getPrimaryIndexDirectory()
    {
        String path = getProperty(PRIMARY_INDEX_PATH);

        // Use path if set, otherwise fall back to temp directory.
        return (null != path ? new File(path) : new File(FileUtil.getTempDirectory(), "labkey_full_text_index"));
    }

    public static void setPrimaryIndexPath(String path)
    {
        setProperty(PRIMARY_INDEX_PATH, path);
    }

    public static String getDirectoryType()
    {
        String type = getProperty(DIRECTORY_TYPE);
        return null == type ? "Default" : type;
    }

    public static void setDirectoryType(String directoryType)
    {
        setProperty(DIRECTORY_TYPE, directoryType);
    }

    private static String getProperty(String key)
    {
        Map<String, String> m = PropertyManager.getProperties(SearchModule.class.getName());
        return m.get(key);
    }

    private static void setProperty(String key, String value)
    {
        PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(SearchModule.class.getName(), true);
        m.put(key, value);
        m.save();
    }
}
