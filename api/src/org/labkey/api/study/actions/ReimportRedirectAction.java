/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.labkey.api.action.RedirectAction;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Set;

/**
 * User: jeckels
 * Date: 8/24/12
 */
@RequiresPermission(InsertPermission.class)
public class ReimportRedirectAction extends RedirectAction<ProtocolIdForm>
{
    @Override
    public URLHelper getSuccessURL(ProtocolIdForm form)
    {
        Set<String> selectedRunIds = DataRegionSelection.getSelected(getViewContext(), true);
        if (selectedRunIds.isEmpty())
        {
            throw new NotFoundException("No run selected");
        }
        AssayProvider provider = form.getProvider();
        if (provider.getReRunSupport() == AssayProvider.ReRunSupport.None)
        {
            throw new NotFoundException("Unable to reimport a run for assays of type " + provider.getName());
        }
        ActionURL url = provider.getImportURL(getContainer(), form.getProtocol());
        String runIdString = selectedRunIds.iterator().next();
        try
        {
            int runId = Integer.parseInt(runIdString);
            ExpRun run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                throw new NotFoundException("No such run found");
            }
            url.setContainer(run.getContainer());
            if (form.getProtocol().getRowId() != run.getProtocol().getRowId())
            {
                throw new NotFoundException("No such run for assay design " + form.getProtocol().getName());
            }
        }
        catch (NumberFormatException e)
        {
            throw new NotFoundException("Invalid runId: " + runIdString);
        }
        url.addParameter("reRunId", runIdString);
        return url;
    }

    @Override
    public boolean doAction(ProtocolIdForm protocolIdForm, BindException errors) throws Exception
    {
        return true;
    }
}
