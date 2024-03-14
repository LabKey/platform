/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.search.SearchController;
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
    private static final String FILE_SIZE_LIMIT = "fileSizeLimitMB";


    public static boolean getCrawlerRunningState()
    {
        String state = getProperty(CRAWLER_RUNNING_STATE);

        if (null != state)
            return "true".equals(state);
        else
            return !AppProps.getInstance().isDevMode();
    }

    public static void setCrawlerRunningState(User user, CrawlerRunningState state)
    {
        setProperty(CRAWLER_RUNNING_STATE, String.valueOf(state.isRunning()));
        audit(user, state.getAuditMessage());
    }

    public static String getUnsubstitutedIndexDirectory()
    {
        String path = getProperty(INDEX_PATH);

        // Use path if set, otherwise fall back to temp directory.
        if (path != null)
        {
            return path;
        }
        else
        {
            File indexParent = FileContentService.get() != null
                    ? FileContentService.get().getSiteDefaultRoot()
                    : FileUtil.getTempDirectory();
            return new File(indexParent, "@labkey_full_text_index").getPath();
        }
    }

    public static void setIndexPath(User user, String path)
    {
        setProperty(INDEX_PATH, path);
        audit(user, "Index Path Set to " + path);
    }

    public static String getDirectoryType()
    {
        String type = getProperty(DIRECTORY_TYPE);
        return null == type ? "Default" : type;
    }

    public static void setDirectoryType(User user, LuceneDirectoryType type)
    {
        setProperty(DIRECTORY_TYPE, type.name());
        audit(user, "Directory type set to " + type.name());
    }

    public static long getFileSizeLimitMB()
    {
        String limit = getProperty(FILE_SIZE_LIMIT);
        return StringUtils.isNotBlank(limit) ? Long.valueOf(limit) : SearchService.DEFAULT_FILE_SIZE_LIMIT;
    }

    public static void setFileSizeLimitMB(User user, int fileSizeLimitMB)
    {
        setProperty(FILE_SIZE_LIMIT, String.valueOf(fileSizeLimitMB));
        audit(user, String.format("File size limit set to %1$s MB", fileSizeLimitMB));
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

    public static void audit(@Nullable User user, String comment)
    {
        SearchController.audit(user, null, null == user ? "(startup property)" : "(admin action)", comment);
    }
}
