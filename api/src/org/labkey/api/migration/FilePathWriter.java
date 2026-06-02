/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.migration;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;

import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;

public class FilePathWriter implements Closeable
{
    private final PrintWriter _out;
    private final Path _rootPath;

    public FilePathWriter(PrintWriter out)
    {
        _out = out;
        _rootPath = FileContentService.get().getFileRoot(ContainerManager.getRoot()).toPath();
    }

    public void write(File file)
    {
        _out.println(_rootPath.relativize(file.toPath()).normalize());
    }

    public void println(String s)
    {
        _out.println(s);
    }

    public void println()
    {
        _out.println();
    }

    @Override
    public void close()
    {
        _out.close();
    }
}
