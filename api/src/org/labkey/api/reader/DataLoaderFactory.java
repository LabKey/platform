/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchService;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 9/30/12
 */
public interface DataLoaderFactory extends SearchService.DocumentParser
{
    @NotNull DataLoader createLoader(InputStream is, boolean hasColumnHeaders) throws IOException;
    @NotNull DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

    @NotNull DataLoader createLoader(File file, boolean hasColumnHeaders) throws IOException;
    @NotNull DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException;

    @NotNull FileType getFileType();

    boolean indexable();
}
