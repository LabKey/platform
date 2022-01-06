/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.exp;

import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.XmlBeansUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public abstract class AbstractFileXarSource extends XarSource
{
    protected Path _xmlFile;

    protected Path getXmlFile()
    {
        return _xmlFile;
    }

    public AbstractFileXarSource(PipelineJob job)
    {
        super(job);
    }

    public AbstractFileXarSource(String description, Container container, User user)
    {
        this(description, container, user, null);
    }

    public AbstractFileXarSource(String description, Container container, User user, @Nullable PipelineJob job)
    {
        super(description, container, user, job);
    }

    @Override
    public ExperimentArchiveDocument getDocument() throws XmlException, IOException
    {
        InputStream fIn = null;

        try
        {
            NetworkDrive.exists(getXmlFile());
            fIn = Files.newInputStream(getXmlFile());
            return ExperimentArchiveDocument.Factory.parse(fIn, XmlBeansUtil.getDefaultParseOptions());
        }
        finally
        {
            if (fIn != null)
            {
                try
                {
                    fIn.close();
                }
                catch (IOException e)
                {
                }
            }
        }
    }

    @Override
    @Nullable
    @Deprecated
    public File getRoot()
    {
        return null != getRootPath()? getRootPath().toFile() : null;
    }

    @Override
    @Nullable
    public Path getRootPath()
    {
        return null != getXmlFile()? getXmlFile().getParent(): null;
    }

    @Override
    public boolean shouldIgnoreDataFiles()
    {
        return false;
    }

    @Override
    public String canonicalizeDataFileURL(String dataFileURL)
    {
        Path xarDirectory = getRootPath();
        URI uri = FileUtil.createUri(dataFileURL);
        if (!uri.isAbsolute())
        {
            Path path = xarDirectory.resolve(dataFileURL);
            String result = _dataFileURLs.get(FileUtil.getAbsolutePath(getXarContext().getContainer(), path));
            if (result != null)
            {
                return result;
            }
            return FileUtil.pathToString(FileUtil.getAbsoluteCaseSensitivePath(getXarContext().getContainer(), path.toUri()));
        }
        else
        {
            return FileUtil.pathToString(FileUtil.getAbsoluteCaseSensitivePath(getXarContext().getContainer(), uri));
        }
    }

    public static Path getLogFileFor(Path f) throws IOException
    {
        Path xarDirectory = f.getParent();
        if (!Files.exists(xarDirectory))
        {
            throw new IOException("Xar file parent directory does not exist");
        }

        String xarShortName = f.getFileName().toString();
        int index = xarShortName.toLowerCase().lastIndexOf(".xml");
        if (index == -1)
        {
            index = xarShortName.toLowerCase().lastIndexOf(".xar");
        }

        if (index != -1)
        {
            xarShortName = xarShortName.substring(0, index);
        }

        return xarDirectory.resolve(xarShortName + LOG_FILE_NAME_SUFFIX);
    }
}
