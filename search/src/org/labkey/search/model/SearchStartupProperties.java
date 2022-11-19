package org.labkey.search.model;

import org.labkey.api.settings.StartupProperty;
import org.labkey.api.util.SafeToRenderEnum;

import java.util.Arrays;

public enum SearchStartupProperties implements StartupProperty, SafeToRenderEnum
{
    indexFilePath("Path to full-text search index"),
    directoryType("Directory type. Valid values: " + Arrays.toString(LuceneDirectoryType.values())),
    indexedFileSizeLimit("Maximum file size limit"),
    pauseCrawler("Pause crawler. Valid values: [true, false]"),
    deleteIndex("Delete the index entirely and force a fast reindex");

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
}
