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
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;

@ActionNames("getRun, getAssayRun")
@RequiresPermission(ReadPermission.class)
public class GetRunAction extends ReadOnlyApiAction<GetRunAction.LoadAssayRunForm>
{
    @Override
    public ApiResponse execute(LoadAssayRunForm form, BindException errors)
    {
        ExpRun run = null;
        if (form.getLsid() != null)
            run = getRun(form.getLsid());
        else if (form.getRunId() != null)
            run = getRun(form.getRunId());
        else
            throw new ApiUsageException("Either lsid or runId is required");

        JSONObject result = new JSONObject();

        ExpProtocol protocol = run.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        var settings = new ExperimentJSONConverter.Settings(form.includeProperties, form.includeInputsAndOutputs, form.includeRunSteps);
        result.put("run", AssayJSONConverter.serializeRun(run, provider, protocol, getUser(), settings));

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
        private String lsid;
        private Integer runId;
        private boolean includeProperties = true;
        private boolean includeInputsAndOutputs = true;
        private boolean includeRunSteps = false;

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

        public boolean isIncludeProperties()
        {
            return includeProperties;
        }

        public void setIncludeProperties(boolean includeProperties)
        {
            this.includeProperties = includeProperties;
        }

        public boolean isIncludeInputsAndOutputs()
        {
            return includeInputsAndOutputs;
        }

        public void setIncludeInputsAndOutputs(boolean includeInputsAndOutputs)
        {
            this.includeInputsAndOutputs = includeInputsAndOutputs;
        }

        public boolean isIncludeRunSteps()
        {
            return includeRunSteps;
        }

        public void setIncludeRunSteps(boolean includeRunSteps)
        {
            this.includeRunSteps = includeRunSteps;
        }
    }
}

