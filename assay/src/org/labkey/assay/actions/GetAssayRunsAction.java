package org.labkey.assay.actions;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

@RequiresPermission(ReadPermission.class)
public class GetAssayRunsAction extends ReadOnlyApiAction<GetAssayRunsAction.AssayRunsForm>
{
    @Override
    public ApiResponse execute(AssayRunsForm assayRunsForm, BindException errors) throws Exception
    {
        List<JSONObject> runs = new ArrayList<>();
        JSONObject result = new JSONObject();

        if (!assayRunsForm.getRunIds().isEmpty() && !assayRunsForm.getLsids().isEmpty())
        {
            return new ApiSimpleResponse("Error", "Must provide either list of runIds or list of lsids.");
        }

        if (!assayRunsForm.getLsids().isEmpty())
        {
            assayRunsForm.getLsids().forEach(lsid -> {
                ExpRun run = ExperimentService.get().getExpRun(lsid);
                if (null != run)
                {
                    runs.add(AssayJSONConverter.serializeRun(run, null, run.getProtocol(), getUser()));
                }
            });
        }
        else if (!assayRunsForm.getRunIds().isEmpty())
        {
            assayRunsForm.getRunIds().forEach(runId -> {
                ExpRun run = ExperimentService.get().getExpRun(runId);
                if (null != run)
                {
                    runs.add(AssayJSONConverter.serializeRun(run, null, run.getProtocol(), getUser()));
                }
            });
        }

        result.put("runs", runs);

        return new ApiSimpleResponse(result);
    }

    static class AssayRunsForm
    {
        List<String> lsids = new ArrayList<>();
        List<Integer> runIds = new ArrayList<>();

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
    }
}
