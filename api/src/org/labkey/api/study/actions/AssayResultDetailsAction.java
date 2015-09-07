/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
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
 * Date: Dec 12, 2008
 */
@RequiresPermission(ReadPermission.class)
public class AssayResultDetailsAction extends BaseAssayAction<DataDetailsForm>
{
    private ExpProtocol _protocol;
    private ExpData _data;
    private Object _dataRowId;

    public ModelAndView getView(DataDetailsForm form, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = form.getProtocol();
        _dataRowId = form.getDataRowId();

        AssayProvider provider = form.getProvider();
        if (!(provider instanceof AbstractAssayProvider))
            throw new RuntimeException("Assay must be derived from AbstractAssayProvider to use the AssayResultDetailsAction");

        AbstractAssayProvider aap = (AbstractAssayProvider) provider;
        _data = aap.getDataForDataRow(_dataRowId, _protocol);
        if (_data == null)
        {
            throw new NotFoundException("Assay ExpData not found for dataRowId: " + _dataRowId);
        }

        if (!_data.getContainer().equals(getContainer()))
        {
            throw new RedirectException(getViewContext().cloneActionURL().setContainer(_data.getContainer()));
        }

        return provider.createResultDetailsView(context, _protocol, _data, _dataRowId);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getContainer();
        ExpRun run = _data.getRun();
        ActionURL batchListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(c, _protocol, null);
        ActionURL runListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, _protocol);
        ActionURL resultsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, _protocol, run.getRowId());

        return super.appendNavTrail(root)
                .addChild(_protocol.getName() + " Batches", batchListURL)
                .addChild(_protocol.getName() + " Runs", runListURL)
                .addChild(run.getName() + " Results", resultsURL)
                .addChild(_dataRowId + " Details");
    }
}
