/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.files;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;

/**
 * User: adam
 * Date: 9/19/13
 * Time: 9:50 AM
 */
public class NoopFileSystemWatcher implements FileSystemWatcher
{
    @Override
    @SuppressWarnings("unchecked")
    public final void addListener(Path directory, FileSystemDirectoryListener listener, Kind<Path>... events) throws IOException
    {
    }

    @Override
    public void removeListener(Path directory, FileSystemDirectoryListener listener)
    {
    }
}
