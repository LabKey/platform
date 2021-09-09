/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

package org.labkey.api.admin;

import java.io.File;
import java.nio.file.Path;

public class ImportException extends Exception
{
    public ImportException(String message)
    {
        super(message);
    }

    public ImportException(String message, Throwable t)
    {
        super(message, t);
    }

    /**
     * Returns a filepath relative to root... this provides path information but hides the pipeline root path.
     */
    public static String getRelativePath(File root, File file)
    {
        return getRelativePath(root.toPath(), file.toPath());
    }

    /**
     * Returns a filepath relative to root... this provides path information but hides the pipeline root path.
     */
    public static String getRelativePath(Path root, Path file)
    {
        String rootPath = root.toAbsolutePath().toString();
        String filePath = file.toAbsolutePath().toString();

        if (filePath.startsWith(rootPath))
            return filePath.substring(rootPath.length());
        else
            return file.toString();
    }
}
