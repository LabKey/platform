/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

/**
 * User: kevink
 * Date: Dec 12, 2008
 */
@RequiresPermission(ACL.PERM_READ)
public class AssayDataDetailsAction extends BaseAssayAction<DataDetailsForm>
{
    private ExpProtocol _protocol;
    private ExpData _data;
    private Object _dataRowId;

    public ModelAndView getView(DataDetailsForm form, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = getProtocol(form);
        _dataRowId = form.getDataRowId();

        AssayProvider provider = AssayService.get().getProvider(_protocol);
        if (!(provider instanceof AbstractAssayProvider))
            throw new RuntimeException("Assay must be derived from AbstractAssayProvider to use the AssayDataDetailsAction");

        AbstractAssayProvider aap = (AbstractAssayProvider)provider;
        _data = aap.getDataForDataRow(_dataRowId);
        if (_data == null)
            HttpView.throwNotFound("Assay ExpData not found for dataRowId: " + _dataRowId);

        return aap.createDataDetailsView(context, _protocol, _data, _dataRowId);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getContainer();
        ExpRun run = _data.getRun();
        ActionURL assayListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(c);
        ActionURL runListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, _protocol);
        ActionURL runDataURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayDataURL(c, _protocol, run.getRowId());

        return root
            .addChild("Assay List", assayListURL)
            .addChild(_protocol.getName() + " Runs", runListURL)
            .addChild(run.getName() + " Run", runDataURL)
            .addChild(_dataRowId + " Details");
    }
}
