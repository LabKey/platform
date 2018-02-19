/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.study.assay;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.assay.AssayUrls;
import org.apache.log4j.Logger;

import java.io.File;

/**
 * User: jeckels
 * Date: Dec 22, 2009
 */
public class FileBasedModuleDataHandler extends AbstractExperimentDataHandler
{
    public static final String NAMESPACE = "RawAssayData";
    private static final DataType RAW_DATA_TYPE = new DataType(NAMESPACE);

    @Override
    public DataType getDataType()
    {
        return RAW_DATA_TYPE;
    }

    public void deleteData(ExpData data, Container container, User user)
    {
        // We don't import these data files directly so no need to delete them
    }

    public ActionURL getContentURL(ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            ExpProtocol p = ExperimentService.get().getExpProtocol(protocol.getRowId());
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(data.getContainer(), p, run.getRowId());
        }
        return null;
    }

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
    }
}
