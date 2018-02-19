/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public abstract class AbstractExperimentDataHandler implements ExperimentDataHandler
{
    public String getFileName(ExpData data, String defaultName)
    {
        return defaultName;
    }

    public void exportFile(ExpData data, File dataFile, User user, OutputStream out) throws ExperimentException
    {
        exportFile(data, dataFile.toPath(), user, out);
    }

    public void exportFile(ExpData data, Path dataFile, User user, OutputStream out) throws ExperimentException
    {
        if (dataFile != null)
        {
            try
            {
                Files.copy(dataFile, out);
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
        }
    }

    public void beforeDeleteData(List<ExpData> data) throws ExperimentException
    {
    }

    public boolean hasContentToExport(ExpData data, File file)
    {
        return hasContentToExport(data, file.toPath());
    }

    public boolean hasContentToExport(ExpData data, Path path)
    {
        if (!FileUtil.hasCloudScheme(path))
        {
            File file = path.toFile();
            return NetworkDrive.exists(file) && file.isFile();
        }
        return Files.exists(path) && !Files.isDirectory(path);
    }

    public void beforeMove(ExpData oldData, Container container, User user) throws ExperimentException
    {
        
    }

    @Override
    public Priority getPriority(ExpData data)
    {
        if (null != getDataType())
        {
            Lsid lsid = new Lsid(data.getLSID());
            if (getDataType().matches(lsid))
            {
                return Priority.HIGH;
            }
        }
        return null;
    }
}
