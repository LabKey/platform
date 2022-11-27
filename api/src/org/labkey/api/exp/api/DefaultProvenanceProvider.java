package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A no-op implementation of ProvenanceService
 * */
public class DefaultProvenanceProvider implements ProvenanceService
{
    @Override
    public boolean isProvenanceSupported()
    {
        return false;
    }

    @Override
    public void addProvenanceInputs(Container container, ExpProtocolApplication app, Set<String> inputLSIDs)
    {
    }

    @Override
    public void addProvenanceOutputs(Container container, ExpProtocolApplication app, Set<String> outputLSIDs)
    {
    }

    @Override
    public void addProvenance(Container container, ExpProtocolApplication app, Set<Pair<String, String>> lsidPairs)
    {
    }

    @Override
    public Set<Pair<String, String>> getProvenanceObjectUris(int protocolAppId)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getProvenanceObjectUriSet(int protocolAppId)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<Pair<Integer, Integer>> getProvenanceObjectIds(int protocolAppId)
    {
        return Collections.emptySet();
    }

    @Override
    public void deleteProvenance(int protocolAppId)
    {
    }

    @Override
    public void deleteRunProvenance(int runId)
    {
    }

    @Override
    public void deleteProvenanceByLsids(Container c, User user, @NotNull Collection<String> lsids, boolean deleteOntologyObjects, Collection<String> deleteEmptyRunsForProtocol)
    {
    }

    @Override
    public void deleteProvenanceByLsids(Container c, User user, @NotNull SQLFragment lsidInFrag, boolean deleteOntologyObjects, Collection<String> deleteEmptyRunsForProtocol)
    {
    }

    @Override
    public void deleteObjectProvenance(int objectId)
    {
    }

    @Override
    public Set<Integer> getProtocolApplications(String lsid)
    {
        return Collections.emptySet();
    }

    @Override
    public List<? extends ExpRun> getRuns(Collection<String> lsids)
    {
        return Collections.emptyList();
    }

    @Override
    public List<? extends ExpRun> getRuns(SQLFragment lsidInFrag)
    {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Set<ExpRun>> getRunsByLsid(Collection<String> lsids)
    {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Set<ExpRun>> getRunsByLsid(SQLFragment lsidInFrag)
    {
        return Collections.emptyMap();
    }

    @Override
    public GUID startRecording(ViewContext context, JSONObject jsonObject)
    {
        return null;
    }

    @Override
    public void addRecordingStep(HttpServletRequest request, GUID recordingId, RecordedAction action)
    {
    }

    @Override
    public ExpRun stopRecording(HttpServletRequest request, GUID recordingId, RecordedAction action, User user, Container container)
    {
        return null;
    }

    @Override
    public ProvenanceRecordingParams createRecordingParams(ViewContext context, JSONObject jsonObject, String recordingType)
    {
        return null;
    }

    @Override
    public RecordedAction createRecordedAction(ViewContext context, @NotNull ProvenanceRecordingParams params)
    {
        return null;
    }

    @Override
    public List<Pair<String, String>> createProvenanceMapFromRows(ViewContext context, ProvenanceRecordingParams params, JSONArray rows, List<Map<String, Object>> responseRows)
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getDatasetProvenanceLsids(User user, Dataset dataset)
    {
        return Collections.emptyList();
    }
}
