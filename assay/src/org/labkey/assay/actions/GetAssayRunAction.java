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

@RequiresPermission(ReadPermission.class)
public class GetAssayRunAction extends ReadOnlyApiAction<GetAssayRunAction.LoadAssayRunForm>
{
    @Override
    public ApiResponse execute(LoadAssayRunForm loadAssayRunForm, BindException errors)
    {
        ExpRun run = ExperimentService.get().getExpRun(loadAssayRunForm.getLsid());
        JSONObject result = new JSONObject();

        result.put("run", AssayJSONConverter.serializeRun(run, null, null, getUser()));

        return new ApiSimpleResponse(result);

    }

    static class LoadAssayRunForm
    {
        String lsid;

        public String getLsid()
        {
            return lsid;
        }

        public void setLsid(String lsid)
        {
            this.lsid = lsid;
        }

    }
}

