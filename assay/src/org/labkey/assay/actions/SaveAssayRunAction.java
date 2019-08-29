package org.labkey.assay.actions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.DefaultExperimentSaveHandler;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentSaveHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

@RequiresPermission(InsertPermission.class)
public class SaveAssayRunAction extends BaseProtocolAPIAction<SimpleApiJsonForm>
{
    @Override
    protected ApiResponse executeAction(ExpProtocol protocol, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        // A user can send in either an array of runs or just a run but not both.  If a user sends in an array of runs
        // then it must have at least one run

        JSONObject runJsonObject = null;
        JSONArray runsJsonArray = null;

        if (rootJsonObject.has(AssayJSONConverter.RUN))
            runJsonObject = rootJsonObject.getJSONObject(AssayJSONConverter.RUN);

        if (rootJsonObject.has(AssayJSONConverter.RUNS))
            runsJsonArray = rootJsonObject.getJSONArray(AssayJSONConverter.RUNS);

        verifyFormJsonObject(runJsonObject, runsJsonArray);

        ExperimentSaveHandler saveHandler = new DefaultExperimentSaveHandler();

        return executeAction(saveHandler, protocol, getAssayProvider(), rootJsonObject, runsJsonArray);
    }

    private ApiResponse executeAction(ExperimentSaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider,
                                      JSONObject rootJsonObject, JSONArray runsJsonArray) throws Exception
    {
        List<ExpRun> runs = new ArrayList<>();
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (int i = 0; i < runsJsonArray.length(); i++)
            {
                JSONObject runJsonObject = runsJsonArray.getJSONObject(i);
                runs.add(saveHandler.handleRunWithoutBatch(getViewContext(), runJsonObject, protocol));
            }

            transaction.commit();
        }
        return AssayJSONConverter.serializeRuns(provider, protocol, runs, getUser());

    }
}
