/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: kevink
 * Date: Feb 11, 2009
 */
@RequiresPermission(ReadPermission.class)
public class AssayRunDetailsAction extends BaseAssayAction<AssayRunDetailsAction.AssayRunDetailsForm>
{
    public static class AssayRunDetailsForm extends ProtocolIdForm
    {
        int _runId;

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }
    }

    private ExpProtocol _protocol;
    private ExpRun _run;

    public ModelAndView getView(AssayRunDetailsForm form, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = form.getProtocol();
        _run = ExperimentService.get().getExpRun(form.getRunId());
        if (_run == null)
        {
            throw new NotFoundException("Assay run not found for runId: " + form.getRunId());
        }

        if (!_run.getContainer().equals(getContainer()))
        {
            throw new RedirectException(getViewContext().cloneActionURL().setContainer(_run.getContainer()));
        }

        AssayProvider provider = AssayService.get().getProvider(_protocol);
        return provider.createRunDetailsView(context, _protocol, _run);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getContainer();
        ActionURL batchListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(c, _protocol, null);
        ActionURL runListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, _protocol);

        return super.appendNavTrail(root)
                .addChild(_protocol.getName() + " Batches", batchListURL)
                .addChild(_protocol.getName() + " Runs", runListURL)
                .addChild(_run.getName() + " Details");
    }
}
