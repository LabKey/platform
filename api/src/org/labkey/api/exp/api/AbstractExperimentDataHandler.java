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

package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.security.User;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Base class for implementors of ExperimentDataHandler. Assumes that the referenced file should be copied as-is, instead
 * of potentially needing to be regenerated for operations like export based on the current contents of the database.
 * User: jeckels
 * Date: Dec 2, 2005
 */
public abstract class AbstractExperimentDataHandler implements ExperimentDataHandler
{

    @Override
    public void exportFile(ExpData data, FileLike dataFile, User user, OutputStream out) throws ExperimentException
    {
        if (dataFile != null)
        {
            try (InputStream is = dataFile.openInputStream())
            {
                is.transferTo(out);
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
        }
    }


    @Override
    public void beforeDeleteData(List<ExpData> data, User user) throws ExperimentException
    {
    }

    @Override
    public boolean hasContentToExport(ExpData data, FileLike file)
    {
        return file != null && file.exists() && file.isFile();
    }

    @Override
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
