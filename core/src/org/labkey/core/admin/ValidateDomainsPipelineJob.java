/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.core.admin;

import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Aug 30, 2011
 */
public class ValidateDomainsPipelineJob extends PipelineJob
{
    public ValidateDomainsPipelineJob(ViewBackgroundInfo info, PipeRoot root)
    {
        super(null, info, root);

        try
        {
            File logFile = File.createTempFile("validateDomains", ".log", root.ensureSystemDirectory());
            setLogFile(logFile);
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Validates that all domains backed by hard tables are in a consistent state";
    }

    @Override
    public void run()
    {
        setStatus(TaskStatus.running);
        getLogger().info("Starting to check domains");
        StorageProvisioner.ProvisioningReport pr = StorageProvisioner.getProvisioningReport();
        getLogger().info(String.format("%d domains use Storage Provisioner", pr.getProvisionedDomains().size()));
        int errorCount = 0;
        for (StorageProvisioner.ProvisioningReport.DomainReport dr : pr.getProvisionedDomains())
        {
            for (String error : dr.getErrors())
            {
                errorCount++;
                getLogger().error(error);
            }
        }
        for (String error : pr.getGlobalErrors())
        {
            errorCount++;
            getLogger().error(error);
        }
        getLogger().info("Check complete, " + errorCount + " errors found");
        setStatus(TaskStatus.complete, "Job finished at: " + DateUtil.nowISO());
    }
}
