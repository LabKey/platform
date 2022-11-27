package org.labkey.assay.actions;

import org.jetbrains.annotations.NotNull;
import org.json.old.JSONObject;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiresPermission(ReadPermission.class)
public class GetAssayRunsAction extends ReadOnlyApiAction<GetAssayRunsAction.AssayRunsForm>
{
    @Override
    public ApiResponse execute(AssayRunsForm assayRunsForm, BindException errors) throws Exception
    {
        List<JSONObject> runs = new ArrayList<>();
        JSONObject result = new JSONObject();
        var settings = new ExperimentJSONConverter.Settings(assayRunsForm.includeProperties, assayRunsForm.includeInputsAndOutputs, assayRunsForm.includeRunSteps);

        if (assayRunsForm.getLsids() != null && !assayRunsForm.getLsids().isEmpty())
        {
            runs = assayRunsForm.getLsids().stream()
                    .map(this::getRun)
                    .map(run -> this.serializeRun(run, settings))
                    .collect(Collectors.toList());
        }
        else if (assayRunsForm.getRunIds() != null && !assayRunsForm.getRunIds().isEmpty())
        {
            runs = assayRunsForm.getRunIds().stream()
                    .map(this::getRun)
                    .map(run -> this.serializeRun(run, settings))
                    .collect(Collectors.toList());
        }
        else
        {
            throw new ApiUsageException("Must provide either list of runIds or list of lsids.");
        }

        result.put("runs", runs);

        return new ApiSimpleResponse(result);
    }

    JSONObject serializeRun(@NotNull ExpRun run, ExperimentJSONConverter.Settings settings)
    {
        ExpProtocol protocol = run.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        return AssayJSONConverter.serializeRun(run, provider, run.getProtocol(), getUser(), settings);
    }

    ExpRun getRun(int runId)
    {
        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (run == null)
            throw new NotFoundException("Run not found: " + runId);

        if (!run.getContainer().equals(getContainer()))
            throw new NotFoundException("Run '" + runId + "' not found in folder: " + getContainer().getPath());

        return run;
    }

    ExpRun getRun(String lsid)
    {
        ExpRun run = ExperimentService.get().getExpRun(lsid);
        if (run == null)
            throw new NotFoundException("Run not found: " + lsid);

        if (!run.getContainer().equals(getContainer()))
            throw new NotFoundException("Run '" + lsid + "' not found in folder: " + getContainer().getPath());

        return run;
    }

    static class AssayRunsForm
    {
        private List<String> lsids = new ArrayList<>();
        private List<Integer> runIds = new ArrayList<>();
        private boolean includeProperties = true;
        private boolean includeInputsAndOutputs = true;
        private boolean includeRunSteps = false;

        public List<String> getLsids()
        {
            return lsids;
        }

        public void setLsids(List<String> lsids)
        {
            this.lsids = lsids;
        }

        public List<Integer> getRunIds()
        {
            return runIds;
        }

        public void setRunIds(List<Integer> runIds)
        {
            this.runIds = runIds;
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
