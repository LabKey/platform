package org.labkey.api.reader;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 9/30/12
 */
public interface DataLoaderFactory
{
    @NotNull DataLoader createLoader(InputStream is, boolean hasColumnHeaders) throws IOException;
    @NotNull DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

    @NotNull DataLoader createLoader(File file, boolean hasColumnHeaders) throws IOException;
    @NotNull DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

    @NotNull FileType getFileType();
}
