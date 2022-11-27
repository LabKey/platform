package org.labkey.assay.actions;

import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentSaveHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

@RequiresPermission(InsertPermission.class)
public class SaveAssayRunsAction extends BaseProtocolAPIAction<SimpleApiJsonForm>
{
    @Override
    protected ApiResponse executeAction(ExpProtocol protocol, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONArray runsJsonArray = null;

        if (rootJsonObject.has(AssayJSONConverter.RUNS))
            runsJsonArray = rootJsonObject.getJSONArray(AssayJSONConverter.RUNS);

        if (runsJsonArray == null)
            throw new IllegalArgumentException("No run array found.");

        if (runsJsonArray.length() == 0)
            throw new IllegalArgumentException("No runs provided. You must provide at least one run in your runs array.");

        ExperimentSaveHandler saveHandler = getExperimentSaveHandler(getAssayProvider());

        return executeAction(saveHandler, protocol, getAssayProvider(),runsJsonArray);
    }

    private ApiResponse executeAction(ExperimentSaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider, JSONArray runsJsonArray) throws Exception
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
        return AssayJSONConverter.serializeRuns(provider, protocol, runs, getUser(), ExperimentJSONConverter.DEFAULT_SETTINGS);

    }
}
