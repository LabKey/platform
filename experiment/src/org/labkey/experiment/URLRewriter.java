/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 28, 2006
 */
public abstract class URLRewriter
{
    protected Map<Path, FileInfo> _files = new HashMap<>();

    private boolean _includeXarXml;

    public URLRewriter()
    {
        this(true);
    }

    public URLRewriter(boolean includeXarXml)
    {
        _includeXarXml = includeXarXml;
    }

    public abstract String rewriteURL(Path path, ExpData data, String role, ExpRun experimentRun, User user) throws ExperimentException;

    public Collection<FileInfo> getFileInfos()
    {
        return _files.values();
    }

    public boolean isIncludeXarXml()
    {
        return _includeXarXml;
    }

    protected static class FileInfo
    {
        private final Path _path;
        private final String _name;
        private final ExperimentDataHandler _handler;
        private final ExpData _data;
        private final User _user;

        public FileInfo(ExpData data, Path path, String name, ExperimentDataHandler handler, User user)
        {
            _path = path;
            _name = name;
            _handler = handler;
            _data = data;
            _user = user;
        }

        public String getName()
        {
            return _handler.getFileName(_data, _name);
        }

        public void writeFile(final OutputStream out) throws ExperimentException, IOException
        {
            if (_handler != null)
            {
                _handler.exportFile(_data, _path, _user, new OutputStream()
                {
                    private boolean _closed = false;

                    private void checkClosed()
                    {
                        if (_closed)
                        {
                            throw new IllegalStateException("Attempting to write to an OutputStream after it has been closed");
                        }
                    }
                    
                    public void write(byte b[]) throws IOException
                    {
                        checkClosed();
                        out.write(b);
                    }

                    public void write(byte b[], int off, int len) throws IOException
                    {
                        checkClosed();
                        out.write(b, off, len);
                    }

                    public void write(int b) throws IOException
                    {
                        checkClosed();
                        out.write(b);
                    }

                    public void close()
                    {
                        _closed = true;
                    }
                });
            }
            else
            {
                Files.copy(_path, out);
            }
        }

        public boolean hasContentToExport()
        {
            if (_handler != null)
            {
                return _handler.hasContentToExport(_data, _path);
            }
            else
            {
                if (!FileUtil.hasCloudScheme(_path))
                {
                    File file = _path.toFile();
                    return NetworkDrive.exists(file) && file.isFile();
                }
                return Files.exists(_path) && !Files.isDirectory(_path);
            }
        }
    }
}
