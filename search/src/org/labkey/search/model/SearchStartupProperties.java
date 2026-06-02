/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.search.SearchService;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.search.SearchModule.SearchIndexStartupHandler;

import java.util.Arrays;

public enum SearchStartupProperties implements StartupProperty
{
    indexFilePath("Path to full-text search index. Supports string substitution of system properties. (See Full-Text Search admin page for details.)"){
        @Override
        public void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value)
        {
            SearchPropertyManager.setIndexPath(null, value);
        }
    },
    directoryType("Directory type. Valid values: " + Arrays.toString(LuceneDirectoryType.values())){
        @Override
        public void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value)
        {
            LuceneDirectoryType type = EnumUtils.getEnum(LuceneDirectoryType.class, value);
            if (null == type)
                LOG.error("Unrecognized value for \"directoryType\": \"{}\"", value);
            else
                SearchPropertyManager.setDirectoryType(null, type);
        }
    },
    indexedFileSizeLimit("Maximum file size limit (MB)"){
        @Override
        public void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value)
        {
            SearchPropertyManager.setFileSizeLimitMB(null, Integer.valueOf(value));
        }
    },
    crawlerState("Pause or start the crawler. Valid values: " + Arrays.toString(CrawlerRunningState.values())){
        @Override
        public void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value)
        {
            CrawlerRunningState state = EnumUtils.getEnum(CrawlerRunningState.class, value);
            if (null == state)
                LOG.error("Unrecognized value for \"crawlerState\": \"{}\"", value);
            else
                SearchPropertyManager.setCrawlerRunningState(null, state);
        }
    },
    deleteIndex("Delete index and clear last indexed, after setting other properties"){
        @Override
        public void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value)
        {
            if (Boolean.parseBoolean(value))
                indexStartupHandler.setDeleteIndex("deleteIndex startup property was set to true");
        }
    },
    indexFull("Initiate an aggressive reindex process, after setting other properties"){
        @Override
        public void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value)
        {
            if (Boolean.parseBoolean(value))
                indexStartupHandler.setIndexFull("indexFull startup property was set to true");
        }
    };

    private static final Logger LOG = LogHelper.getLogger(SearchStartupProperties.class, "Search startup property errors");

    private final String _description;

    SearchStartupProperties(String description)
    {
        _description = description;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public abstract void setProperty(@NotNull SearchService ss, SearchIndexStartupHandler indexStartupHandler, String value);
}
