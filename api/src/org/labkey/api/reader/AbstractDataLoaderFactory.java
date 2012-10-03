/*
 * Copyright (c) 2012 LabKey Corporation
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 9/30/12
 */
public abstract class AbstractDataLoaderFactory implements DataLoaderFactory
{
    @NotNull
    public DataLoader createLoader(InputStream is, boolean hasColumnHeaders) throws IOException
    {
        return createLoader(is, hasColumnHeaders, null);
    }

    @NotNull
    public DataLoader createLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        return createLoader(file, hasColumnHeaders, null);
    }
}
