/*
 * Copyright (c) 2019-2026 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;

@RequiresPermission(ReadPermission.class)
public class GetAssayRunAction extends ReadOnlyApiAction<GetAssayRunAction.LoadAssayRunForm>
{
    @Override
    public ApiResponse execute(LoadAssayRunForm loadAssayRunForm, BindException errors)
    {
        ExpRun run;
        if (loadAssayRunForm.getLsid() != null)
            run = getRun(loadAssayRunForm.getLsid());
        else if (loadAssayRunForm.getRunId() != null)
            run = getRun(loadAssayRunForm.getRunId());
        else
            throw new ApiUsageException("Either lsid or runId is required");

        JSONObject result = new JSONObject();

        ExpProtocol protocol = run.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        result.put("run", AssayJSONConverter.serializeRun(run, provider, protocol, getUser(), ExperimentJSONConverter.DEFAULT_SETTINGS));

        return new ApiSimpleResponse(result);

    }

    ExpRun getRun(int runId)
    {
        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (run == null)
            throw new NotFoundException("Run not found: " + runId);

        if (!run.getContainer().equals(getContainer()))
            throw new NotFoundException("Run not found in folder: " + runId);

        return run;
    }

    ExpRun getRun(String lsid)
    {
        ExpRun run = ExperimentService.get().getExpRun(lsid);
        if (run == null)
            throw new NotFoundException("Run not found: " + lsid);

        if (!run.getContainer().equals(getContainer()))
            throw new NotFoundException("Run not found in folder: " + lsid);

        return run;
    }


    static class LoadAssayRunForm
    {
        String lsid;
        Integer runId;

        public String getLsid()
        {
            return lsid;
        }

        public void setLsid(String lsid)
        {
            this.lsid = lsid;
        }

        public Integer getRunId()
        {
            return runId;
        }

        public void setRunId(Integer runId)
        {
            this.runId = runId;
        }
    }
}

