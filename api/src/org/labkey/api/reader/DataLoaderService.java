/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.reader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileType;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 9/30/12
 */
public interface DataLoaderService
{
    static DataLoaderService get()
    {
        return ServiceRegistry.get(DataLoaderService.class);
    }

    void registerFactory(@NotNull DataLoaderFactory factory);

    @Nullable
    DataLoaderFactory findFactory(File file, @Nullable FileType guessFormat);

    @Nullable
    DataLoaderFactory findFactory(File file, String contentType, @Nullable FileType guessFormat);

    @Nullable
    DataLoaderFactory findFactory(String filename, String contentType, InputStream is, @Nullable FileType guessFormat);

    DataLoader createLoader(String filename, String contentType, InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException;

    DataLoader createLoader(MultipartFile file, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException;

    DataLoader createLoader(Resource r, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException;

    DataLoader createLoader(File file, String contentType, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException;
}
