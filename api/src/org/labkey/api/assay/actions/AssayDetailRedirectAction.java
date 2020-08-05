/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
/*
 * User: brittp
 * Date: Mar 17, 2009
 * Time: 1:47:38 PM
 */

@RequiresPermission(ReadPermission.class)
public class AssayDetailRedirectAction extends SimpleRedirectAction<AssayDetailRedirectAction.AssayDetailsForm>
{
    public static class AssayDetailsForm
    {
        private Integer _runId;

        public Integer getRunId()
        {
            return _runId;
        }

        public void setRunId(Integer runId)
        {
            _runId = runId;
        }
    }

    @Override
    public ActionURL getRedirectURL(AssayDetailsForm form)
    {
        if (form.getRunId() == null)
        {
            throw new NotFoundException();
        }
        ExpRun run = ExperimentService.get().getExpRun(form.getRunId().intValue());
        if (run == null)
            throw new NotFoundException("The assay run that produced the data has been deleted.");
        ActionURL url = LsidManager.get().getDisplayURL(run.getLSID());
        if (url != null)
            return url;
        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(run.getContainer(), run.getProtocol(), run.getRowId());
    }
}