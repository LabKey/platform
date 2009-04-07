/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

/**
 * <code>PipelinePerlClusterSupport</code> implements support for the old Perl Cluster
 * Pipeline.  This functionality is deprecated, and no new Perl implementations should
 * be created.
 * <p/>
 * Created: Oct 31, 2005
 *
 * @author bmaclean
 */
@Deprecated
public class PipelinePerlClusterSupport
{
    public void preDeleteStatusFile(PipelineStatusFile sf) throws PipelineProvider.StatusUpdateException
    {
        try
        {
            if (!PipelineService.get().usePerlPipeline(sf.lookupContainer()))
                return;
        }
        catch (SQLException e)
        {
            throw new PipelineProvider.StatusUpdateException("Server attempting to delete status for " + sf.getDescription(), e);
        }

        // If it is a cluster .status file, and the status is "ERROR"
        // delete the status file also to keep from leaving a stalled
        // job around forever.  If the job is still running, it will
        // recreate the status entry in this case.
        if (PipelineJob.FT_PERL_STATUS.isType(sf.getFilePath()))
        {
            File fileStatus = new File(sf.getFilePath());
            if (PipelineJob.ERROR_STATUS.equals(sf.getStatus()))
                fileStatus.delete();
            
            if (NetworkDrive.exists(fileStatus))
            {
                throw new PipelineProvider.StatusUpdateException("Failed to delete existing status file " +
                        sf.getFilePath() + ". Job still in progress.");
            }
        }
    }

    public void preCompleteStatusFile(PipelineStatusFile sf) throws PipelineProvider.StatusUpdateException
    {
        try
        {
            if (!PipelineService.get().usePerlPipeline(sf.lookupContainer()))
                return;
        }
        catch (SQLException e)
        {
            throw new PipelineProvider.StatusUpdateException("Server attempting to complete status for " + sf.getDescription(), e);
        }

        // Make sure file system stays in synch.
        if (NetworkDrive.exists(new File(sf.getFilePath())))
        {
            try
            {
                sf.synchDiskStatus();
            }
            catch (IOException eio)
            {
                throw new PipelineProvider.StatusUpdateException(eio.getMessage(), eio);
            }
        }
    }

    public boolean isStatusViewableFile(Container container, String name, String basename)
    {
        try
        {
            if (!PipelineService.get().usePerlPipeline(container))
                return false;
        }
        catch (SQLException e)
        {
            return false;
        }

        // Show cluster specific files
        if (PipelineJob.FT_PERL_STATUS.isMatch(name, basename))
            return true;
        if (name.startsWith("pipe") && name.endsWith(".log"))
            return true;
        if (!name.startsWith(basename) || name.length() == basename.length() ||
                name.charAt(basename.length()) != '.')
            return false;
        return name.endsWith(".out") || name.endsWith(".err") || name.endsWith(".status");
    }

    public List<PipelineProvider.StatusAction> addStatusActions(Container container, List<PipelineProvider.StatusAction> actions)
    {
        try
        {
            if (!PipelineService.get().usePerlPipeline(container))
                return actions;
        }
        catch (SQLException e)
        {
            // Don't add actions, if it is not possible to tell whether this
            // container supports Perl cluster pipeline.
            return actions;
        }

        if (actions == null)
            actions = new ArrayList<PipelineProvider.StatusAction>();
        else
        {
            // Remove any existing retry button.
            for (int i = 0; i < actions.size(); i++)
            {
                if (PipelineProvider.CAPTION_RETRY_BUTTON.equals(actions.get(i).getLabel()))
                {
                    actions.remove(i);
                    break;
                }
            }
        }
        
        actions.add(new PipelineProvider.StatusAction(PipelineProvider.CAPTION_RETRY_BUTTON)
        {
            @Override
            public boolean isVisible(PipelineStatusFile statusFile)
            {
                return PipelineJob.ERROR_STATUS.equals(statusFile.getStatus());
            }
        });
        return actions;
    }

    public ActionURL handleStatusAction(ViewContext ctx, String name, PipelineStatusFile sf)
            throws PipelineProvider.HandlerException
    {
        if (!PipelineProvider.CAPTION_RETRY_BUTTON.equals(name))
            return null;
        
        try
        {
            if (!PipelineService.get().usePerlPipeline(sf.lookupContainer()))
                return null;
        }
        catch (SQLException e)
        {
            throw new PipelineProvider.HandlerException("Server error attempting retry.", e);
        }

        if (PipelineJob.ERROR_STATUS.equals(sf.getStatus()))
        {
            File fileStatus = new File(sf.getFilePath());
            File fileStatusErr = new File(sf.getFilePath() + ".err");

            try
            {
                sf.synchDiskStatus();
            }
            catch (IOException e)
            {
                throw new PipelineProvider.HandlerException("Failed to update status file " + fileStatus + ".", e);
            }

            if (NetworkDrive.exists(fileStatus))
            {
                if (NetworkDrive.exists(fileStatusErr))
                    fileStatus = fileStatusErr;

                if (!fileStatus.delete())
                    throw new PipelineProvider.HandlerException("Failed to delete status file " + fileStatus + ".", null);
            }
            else
            {
                throw new PipelineProvider.HandlerException("File not found " + sf.getFilePath() + ".", null);
            }

            sf.setStatus("UNKNOWN");
            sf.setInfo("retry=Retry on next cycle.");

            try
            {
                ViewBackgroundInfo info = new ViewBackgroundInfo(ctx.getContainer(),
                        ctx.getUser(), ctx.getActionURL());
                PipelineService.get().setStatusFile(info, sf);
            }
            catch (Exception e)
            {
                throw new PipelineProvider.HandlerException("Failed to set retry status for '" + sf.getFilePath() + "'.", e);
            }
        }

        return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(ctx.getContainer(), sf.getRowId());
    }
}
