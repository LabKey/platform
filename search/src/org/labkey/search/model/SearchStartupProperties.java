package org.labkey.search.model;

import org.labkey.api.settings.StartupProperty;
import org.labkey.api.util.SafeToRenderEnum;

import java.util.Arrays;

public enum SearchStartupProperties implements StartupProperty, SafeToRenderEnum
{
    indexFilePath("Path to full-text search index"),
    directoryType("Directory type. Valid values: " + Arrays.toString(LuceneDirectoryType.values())),
    indexedFileSizeLimit("Maximum file size limit"),
    pauseCrawler("Pause crawler. Value vales: [true, false]"),
    deleteIndex("Delete the index entirely");

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
