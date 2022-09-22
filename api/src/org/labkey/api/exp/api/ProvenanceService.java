package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service to include non-data {@link ExpObject} and non-material to a run's ProtocolApplication steps for the purposes of lineage.
 * This service can add provenance to an {@link ExpProtocolApplication} using one of the add Methods.
 * Also, provides support to record provenance information across LabKey HTTP API calls.
 * */
public interface ProvenanceService
{
    ProvenanceService _defaultProvider = new DefaultProvenanceProvider();

    String PROVENANCE_PROPERTY_PREFIX = "prov";

    String PROVENANCE_OBJECT_INPUTS = "objectInputs";
    String PROVENANCE_INPUT_PROPERTY = PROVENANCE_PROPERTY_PREFIX + ":" + PROVENANCE_OBJECT_INPUTS;

    String PROVENANCE_OBJECT_OUTPUTS = "objectOutputs";
    String PROVENANCE_OUTPUT_PROPERTY = PROVENANCE_PROPERTY_PREFIX + ":" + PROVENANCE_OBJECT_OUTPUTS;

    String PROVENANCE_OBJECT_MAP = "provenanceMap";

    String PROVENANCE_RECORDING_IDS = "ProvenanceRecordingIds";

    String PROVENANCE_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:ProvenanceProtocol";

    String RECORDING_ID = "recordingId";
    String START_RECORDING = "StartRecording";
    String ADD_RECORDING = "AddRecording";
    String END_RECORDING = "EndRecording";

    String MATERIAL_INPUTS = "materialInputs";
    String MATERIAL_OUTPUTS = "materialOutputs";
    String DATA_INPUTS = "dataInputs";
    String DATA_OUTPUTS = "dataOutputs";
    String PROPERTIES = "properties";

    String ACTIVITY_DATE = "activityDate";
    String START_TIME = "startTime";
    String END_TIME = "endTime";
    String RECORD_COUNT = "recordCount";
    String COMMENTS = "comments";

    @NotNull
    static ProvenanceService get()
    {
        ProvenanceService svc = ServiceRegistry.get().getService(ProvenanceService.class);
        return svc != null ? svc : _defaultProvider;
    }

    static void setInstance(ProvenanceService impl)
    {
        ServiceRegistry.get().registerService(ProvenanceService.class, impl);
    }

    /**
     * Determines whether the provider returned supports provenance;
     */
    boolean isProvenanceSupported();

    void addProvenanceInputs(Container container, ExpProtocolApplication app, Set<String> inputLSIDs);

    void addProvenanceOutputs(Container container, ExpProtocolApplication app, Set<String> outputLSIDs);

    void addProvenance(Container container, ExpProtocolApplication app, Set<Pair<String, String>> lsidPairs);

    /**
     * Get list of provenance input LSIDs and output LSIDs for a protocol application.
     */
    Set<Pair<String,String>> getProvenanceObjectUris(int protocolAppId);

    /**
     * Get all input and output lsids for a protocol application.
     */
    Set<String> getProvenanceObjectUriSet(int protocolAppId);

    /**
     * Get list of provenance input object IDs and output object IDs for a protocol application.
     */
    Set<Pair<Integer, Integer>> getProvenanceObjectIds(int protocolAppId);

    /**
     * Delete provenance for a single protocol application.
     */
    void deleteProvenance(int protocolAppId);

    /**
     * Delete provenance for all protocol applications within the run.
     */
    void deleteRunProvenance(int runId);

    void deleteProvenanceByLsids(
            Container c, User user, @NotNull Collection<String> lsids,
            boolean deleteEdgesAndOntologyObjects, Collection<String> deleteEmptyRunsForProtocol);

    void deleteProvenanceByLsids(
            Container c, User user, @NotNull SQLFragment lsidInFrag,
            boolean deleteOntologyObjects, Collection<String> deleteEmptyRunsForProtocol);


    /**
     * Delete provenance for a assay result row.
     */
    void deleteObjectProvenance(int objectId);

    /**
     * Get protocol applications for the lsid
     */
    Set<Integer> getProtocolApplications(String lsid);

    /**
     * Get the ExpRun referenced by the set of LSIDs
     */
    List<? extends ExpRun> getRuns(Collection<String> lsids);
    List<? extends ExpRun> getRuns(SQLFragment lsidInFrag);

    /**
     * Get the ExpRun referenced by the set of LSIDs
     */
    Map<String, Set<ExpRun>> getRunsByLsid(Collection<String> lsids);
    Map<String, Set<ExpRun>> getRunsByLsid(SQLFragment lsidInFrag);

    /**
     * Start a recording session, place RecordedActionSet in http session state and
     * @return a GUID as the recording id.
     */
    GUID startRecording(ViewContext context, JSONObject jsonObject) throws ValidationException;

    /**
     * Get the current recording session from http session state, and add the actionSet
     */
    void addRecordingStep(HttpServletRequest request, GUID recordingId, RecordedAction action);

    /**
     *  Get the recording from session state and create an ExpRun
     */
    ExpRun stopRecording(HttpServletRequest request, GUID recordingId, RecordedAction action, User user, Container container) throws ExperimentException, ValidationException;

    /**
     * Helper method to create recording params object
     */
    ProvenanceRecordingParams createRecordingParams(ViewContext context, JSONObject jsonObject, String recordingType) throws ValidationException;

    /**
     * Helper method to construct a RecordedAction from a ProvenanceRecordingParams object.
     */
    RecordedAction createRecordedAction(ViewContext context, @NotNull ProvenanceRecordingParams params);

    /**
     * Extract the provenance map information from the data rows
     *
     * @param context
     * @param params a ProveanceRecordingParams object
     * @param rows the input rows
     * @param responseRows the inserted or updated rows
     * @return
     */
    List<Pair<String, String>> createProvenanceMapFromRows(ViewContext context, ProvenanceRecordingParams params, JSONArray rows, List<Map<String, Object>> responseRows);

    /**
     * Returns the rows of dataset involved in provenance
     */
    Collection<String> getDatasetProvenanceLsids(User user, Dataset dataset);
}
