package org.labkey.api.reader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileType;
import org.labkey.api.webdav.WebdavResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 9/30/12
 */
public class DataLoaderService
{
    public static I get()
    {
        return ServiceRegistry.get(DataLoaderService.I.class);
    }

    public interface I
    {
        public void registerFactory(@NotNull DataLoaderFactory factory);

        @Nullable public DataLoaderFactory findFactory(File file);
        @Nullable public DataLoaderFactory findFactory(String filename, InputStream is);

        public DataLoader createLoader(String filename, InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

        public DataLoader createLoader(MultipartFile file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

        public DataLoader createLoader(Resource r, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

    }
}
