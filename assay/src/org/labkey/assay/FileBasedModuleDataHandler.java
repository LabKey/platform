/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.assay;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

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

    @Override
    public void deleteData(ExpData data, Container container, User user)
    {
        // We don't import these data files directly so no need to delete them
    }

    @Override
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

    @Override
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context)
    {
    }
}
