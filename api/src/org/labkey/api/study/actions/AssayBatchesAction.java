/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.*;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.data.DataRegionSelection;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:30:05 PM
*/
@RequiresPermission(ACL.PERM_READ)
public class AssayBatchesAction extends BaseAssayAction<AssayRunsAction.AssayRunsForm>
{
    private ExpProtocol _protocol;

    public ModelAndView getView(AssayRunsAction.AssayRunsForm summaryForm, BindException errors) throws Exception
    {
        if (summaryForm.getClearDataRegionSelectionKey() != null)
            DataRegionSelection.clearAll(getViewContext(), summaryForm.getClearDataRegionSelectionKey());
        _protocol = getProtocol(summaryForm);
        return new AssayBatchesView(_protocol, false);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild(_protocol.getName() + " Batches");
    }

    public AppBar getAppBar()
    {
        return getAppBar(_protocol);
    }
}
