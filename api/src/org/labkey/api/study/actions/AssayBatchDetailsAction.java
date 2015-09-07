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

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

/**
 * User: kevink
 * Date: Feb 11, 2009
 */
@RequiresPermission(ReadPermission.class)
public class AssayBatchDetailsAction extends BaseAssayAction<AssayBatchDetailsAction.AssayBatchDetailsForm>
{
    public static class AssayBatchDetailsForm extends ProtocolIdForm
    {
        int _batchId;

        public int getBatchId()
        {
            return _batchId;
        }

        public void setBatchId(int batchId)
        {
            _batchId = batchId;
        }
    }

    private ExpProtocol _protocol;
    private ExpExperiment _exp;

    public ModelAndView getView(AssayBatchDetailsForm form, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = form.getProtocol();
        _exp = ExperimentService.get().getExpExperiment(form.getBatchId());
        if (_exp == null)
        {
            throw new NotFoundException("Assay batch not found for runId: " + form.getBatchId());
        }

        if (!_exp.getContainer().equals(getContainer()))
        {
            throw new RedirectException(getViewContext().cloneActionURL().setContainer(_exp.getContainer()));
        }

        AssayProvider provider = AssayService.get().getProvider(_protocol);
        return provider.createBatchDetailsView(context, _protocol, _exp);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getContainer();
        ActionURL batchListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(c, _protocol, null);

        return super.appendNavTrail(root)
                .addChild(_protocol.getName() + " Batches", batchListURL)
                .addChild(_exp.getName() + " Details");
    }
}
