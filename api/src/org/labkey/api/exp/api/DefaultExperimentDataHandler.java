/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.XarContext;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;

/**
 * A default, essentially no-op handler for {@link ExpData} that are not recognized by any other handlers.
 * User: jeckels
 * Date: Jul 10, 2007
 */
public class DefaultExperimentDataHandler extends AbstractExperimentDataHandler
{
    @Override
    public DataType getDataType()
    {
        return null;
    }

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        log.debug("No ExperimentDataHandler registered for data file " + data.getDataFileURI() + ", no special loading will be done on this file.");
    }

    public ActionURL getContentURL(ExpData data)
    {
        return null;
    }

    public void deleteData(ExpData data, Container container, User user)
    {
        // Do nothing
    }

    public Handler.Priority getPriority(ExpData data)
    {
        return Handler.Priority.LOW;
    }
}
