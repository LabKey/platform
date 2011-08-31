package org.labkey.core.admin;

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

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
        try
        {
            setStatus("RUNNING");
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
            getLogger().info("Check complete, " + errorCount + " errors found");
            setStatus(PipelineJob.COMPLETE_STATUS, "Job finished at: " + DateUtil.nowISO());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
