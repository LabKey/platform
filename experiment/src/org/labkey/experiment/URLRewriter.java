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

import org.apache.commons.io.FileUtils;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 28, 2006
 */
public abstract class URLRewriter
{
    protected Map<File, FileInfo> _files = new HashMap<>();

    private boolean _includeXarXml;

    public URLRewriter()
    {
        this(true);
    }

    public URLRewriter(boolean includeXarXml)
    {
        _includeXarXml = includeXarXml;
    }

    public abstract String rewriteURL(File f, ExpData data, String role, ExpRun experimentRun) throws ExperimentException;

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
        private final File _file;
        private final String _name;
        private final ExperimentDataHandler _handler;
        private final ExpData _data;

        public FileInfo(ExpData data, File file, String name, ExperimentDataHandler handler)
        {
            _file = file;
            _name = name;
            _handler = handler;
            _data = data;
        }

        public String getName()
        {
            return _name;
        }

        public void writeFile(final OutputStream out) throws ExperimentException, IOException
        {
            if (_handler != null)
            {
                _handler.exportFile(_data, _file, new OutputStream()
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
                FileUtils.copyFile(_file, out);
            }
        }

        public boolean hasContentToExport()
        {
            if (_handler != null)
            {
                return _handler.hasContentToExport(_data, _file);
            }
            else
            {
                return NetworkDrive.exists(_file) && _file.isFile();
            }
        }
    }
}
