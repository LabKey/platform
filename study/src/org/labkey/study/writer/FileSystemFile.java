/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.util.FileUtil;
import org.labkey.api.util.VirtualFile;

import java.io.*;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:30:23 PM
 */
public class FileSystemFile implements VirtualFile
{
    private File _root;

    public FileSystemFile(File root)
    {
        ensureWriteableDirectory(root);

        _root = root;
    }

    public PrintWriter getPrintWriter(String fileName) throws IOException
    {
        File file = new File(_root, makeLegalName(fileName));

        return new PrintWriter(file);
    }

    public void makeDir(String path) throws FileNotFoundException, UnsupportedEncodingException
    {
        String[] parts = path.split("/");

        File parent = _root;

        for (String part : parts)
        {
            parent = new File(parent, makeLegalName(part));
            ensureWriteableDirectory(parent);
        }
    }

    public VirtualFile getDir(String name)
    {
        return new FileSystemFile(new File(_root, makeLegalName(name)));
    }

    public String makeLegalName(String name)
    {
        return makeLegal(name);
    }

    public static String makeLegal(String name)
    {
        return FileUtil.makeLegalName(name);
    }

    public static void ensureWriteableDirectory(File dir)
    {
        if (!dir.exists())
            //noinspection ResultOfMethodCallIgnored
            dir.mkdir();

        if (!dir.isDirectory())
            throw new IllegalStateException(dir.getAbsolutePath() + " is not a directory.");

        if (!dir.canWrite())
            throw new IllegalStateException("Can't write to " + dir.getAbsolutePath());
    }
}
