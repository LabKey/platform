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

package org.labkey.assay.actions;

import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.actions.AssayRunsAction;
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.view.AssayBatchesView;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
 * Date: Jul 26, 2007
 * Time: 7:30:05 PM
 */
@RequiresPermission(ReadPermission.class)
public class AssayBatchesAction extends BaseAssayAction<AssayRunsAction.AssayRunsForm>
{
    private ExpProtocol _protocol;

    @Override
    public ModelAndView getView(AssayRunsAction.AssayRunsForm summaryForm, BindException errors)
    {
        ViewContext context = getViewContext();
        if (summaryForm.getClearDataRegionSelectionKey() != null)
            DataRegionSelection.clearAll(context, summaryForm.getClearDataRegionSelectionKey());

        _protocol = summaryForm.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        ModelAndView resultsView = provider.createBatchesView(context, _protocol);
        setHelpTopic("workWithAssayData#batch");
        if (resultsView != null)
            return resultsView;
        return new AssayBatchesView(_protocol, false);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        super.addNavTrail(root);
        root.addChild(_protocol.getName() + " Batches");
    }
}
