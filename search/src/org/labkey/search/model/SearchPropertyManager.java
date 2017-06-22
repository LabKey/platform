/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
    private static final String CRAWLER_RUNNING_STATE = "runningState";
    private static final String INDEX_PATH = "primaryIndexPath";  // Note: don't change this legacy name
    private static final String DIRECTORY_TYPE = "directoryType";

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

    public static File getIndexDirectory()
    {
        String path = getProperty(INDEX_PATH);

        // Use path if set, otherwise fall back to temp directory.
        return (null != path ? new File(path) : new File(FileUtil.getTempDirectory(), "labkey_full_text_index"));
    }

    public static void setIndexPath(String path)
    {
        setProperty(INDEX_PATH, path);
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
        Map<String, String> m = PropertyManager.getProperties(CATEGORY);
        return m.get(key);
    }

    private static void setProperty(String key, String value)
    {
        PropertyManager.PropertyMap m = PropertyManager.getWritableProperties(CATEGORY, true);
        m.put(key, value);
        m.save();
    }
}
