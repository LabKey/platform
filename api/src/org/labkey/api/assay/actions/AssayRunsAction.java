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

package org.labkey.api.assay.actions;

import org.apache.commons.io.FileUtils;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunsView;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.PipelineDataCollector;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;

import static org.labkey.api.assay.AssayFileWriter.TEMP_DIR_NAME;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:30:05 PM
*/
@RequiresPermission(ReadPermission.class)
public class AssayRunsAction extends BaseAssayAction<AssayRunsAction.AssayRunsForm>
{
    public static class AssayRunsForm extends ProtocolIdForm
    {
        private String _clearDataRegionSelectionKey;

        public String getClearDataRegionSelectionKey()
        {
            return _clearDataRegionSelectionKey;
        }

        public void setClearDataRegionSelectionKey(String clearDataRegionSelectionKey)
        {
            _clearDataRegionSelectionKey = clearDataRegionSelectionKey;
        }
    }

    private ExpProtocol _protocol;

    public AssayRunsAction()
    {
        super();
    }

    public AssayRunsAction(ViewContext context, ExpProtocol protocol)
    {
        super();
        setViewContext(context);
        _protocol = protocol;
    }

    @Override
    public ModelAndView getView(AssayRunsForm summaryForm, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        if (summaryForm.getClearDataRegionSelectionKey() != null)
            DataRegionSelection.clearAll(context, summaryForm.getClearDataRegionSelectionKey());

        _protocol = summaryForm.getProtocol();
        AssayProvider provider = summaryForm.getProvider();

        ModelAndView resultsView = provider.createRunsView(context, _protocol);
        setHelpTopic("workWithAssayData#runs");

        // If canceling out of transform warning, cleanup files
        if(summaryForm.getUploadAttemptID() != null)
        {
            File tempDir = TsvDataExchangeHandler.removeWorkingDirectory(summaryForm, getUser());
            if(null != tempDir && tempDir.exists())
            {
                if (tempDir.getParentFile().isDirectory() && tempDir.getParentFile().getName().equals(TEMP_DIR_NAME)) // tempDir is at .uploadTemp/tempGUID for temp import files
                    FileUtils.deleteDirectory(tempDir);
                else // don't delete files on the server's file system that are not temp files, but do remove them from file queue in session.
                    PipelineDataCollector.clearFileQueue(context.getRequest().getSession(true), context.getContainer(), _protocol);
            }

        }

        if (resultsView != null)
            return resultsView;
        return new AssayRunsView(_protocol, false, errors);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        super.addNavTrail(root);
        root.addChild(_protocol.getName() + " Batches", PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(getContainer(), _protocol, null));
        root.addChild(_protocol.getName() + " Runs");
    }
}
