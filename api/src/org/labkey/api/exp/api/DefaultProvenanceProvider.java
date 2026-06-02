/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import jakarta.servlet.http.HttpServletRequest;
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
    public OntologyManager.RowCallback getAssayRowCallback(ExpRun run, Container container)
    {
        return OntologyManager.NO_OP_ROW_CALLBACK;
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
    public Set<Pair<String, String>> getProvenanceObjectUris(long protocolAppId)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getProvenanceObjectUriSet(long protocolAppId)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<Pair<Long, Long>> getProvenanceObjectIds(long protocolAppId)
    {
        return Collections.emptySet();
    }

    @Override
    public void deleteProvenance(long protocolAppId)
    {
    }

    @Override
    public void deleteRunProvenance(long runId)
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
    public void deleteObjectProvenance(long objectId)
    {
    }

    @Override
    public Set<Long> getProtocolApplications(String lsid)
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
