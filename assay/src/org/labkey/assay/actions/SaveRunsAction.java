package org.labkey.assay.actions;

import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentSaveHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

@ActionNames("saveRuns, saveAssayRuns")
@RequiresPermission(InsertPermission.class)
public class SaveRunsAction extends BaseProtocolAPIAction<SimpleApiJsonForm>
{
    @Override
    protected ApiResponse executeAction(@Nullable ExpProtocol protocol, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONArray runsJsonArray = null;

        if (rootJsonObject.has(AssayJSONConverter.RUNS))
            runsJsonArray = rootJsonObject.getJSONArray(AssayJSONConverter.RUNS);

        if (runsJsonArray == null)
            throw new IllegalArgumentException("No run array found.");

        if (runsJsonArray.length() == 0)
            throw new IllegalArgumentException("No runs provided. You must provide at least one run in your runs array.");

        AssayProvider provider = getAssayProvider();
        List<ExpRun> runs = executeAction(protocol, provider, runsJsonArray);

        // serialization options - default values match GetRunsAction
        boolean includeProperties = rootJsonObject.optBoolean("includeProperties", true);
        boolean includeInputsAndOutputs = rootJsonObject.optBoolean("includeInputsAndOutputs", true);
        boolean includeRunSteps = rootJsonObject.optBoolean("includeRunSteps", false);
        var settings = new ExperimentJSONConverter.Settings(includeProperties, includeInputsAndOutputs, includeRunSteps);

        return AssayJSONConverter.serializeRuns(provider, protocol, runs, getUser(), settings);
    }

    private List<ExpRun> executeAction(@Nullable ExpProtocol protocol, @Nullable AssayProvider provider, JSONArray runsJsonArray) throws Exception
    {
        List<ExpRun> runs = new ArrayList<>();
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (int i = 0; i < runsJsonArray.length(); i++)
            {
                JSONObject runJsonObject = runsJsonArray.getJSONObject(i);

                // If provided, validate the run protocol matches the protocol specified at the action level
                ExpProtocol runProtocol = DefaultExperimentSaveHandler.lookupProtocol(getViewContext(), runJsonObject.optJSONObject(ExperimentJSONConverter.PROTOCOL));
                if (protocol == null && runProtocol == null)
                    throw new IllegalArgumentException("Top-level assayId or assayName or protocolName property or run-level protocol object required");

                if (protocol != null && runProtocol != null && !protocol.equals(runProtocol))
                    throw new IllegalArgumentException("The run protocol '" + runProtocol.getName() + "' does not match the top-level action protocol '" + protocol.getName() + "'");

                if (runProtocol == null)
                    runProtocol = protocol;

                ExperimentSaveHandler saveHandler = getExperimentSaveHandler(provider);
                runs.add(saveHandler.handleRunWithoutBatch(getViewContext(), runJsonObject, runProtocol));
            }

            transaction.commit();
        }

        return runs;
    }
}
