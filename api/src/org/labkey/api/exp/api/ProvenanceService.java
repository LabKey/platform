package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service to include non-data {@link ExpObject} and non-material to a run's ProtocolApplication steps for the purposes of lineage.
 * This service can add provenance to an {@link ExpProtocolApplication} using one of teh add Methods.
 * */
public interface ProvenanceService
{
    String PROVENANCE_PROPERTY_PREFIX = "prov";

    String PROVENANCE_OBJECT_INPUTS = "objectInputs";
    String PROVENANCE_INPUT_PROPERTY = PROVENANCE_PROPERTY_PREFIX + ":" + PROVENANCE_OBJECT_INPUTS;

    String PROVENANCE_OBJECT_OUTPUTS = "objectOutputs";
    String PROVENANCE_OUTPUT_PROPERTY = PROVENANCE_PROPERTY_PREFIX + ":" + PROVENANCE_OBJECT_OUTPUTS;

    static ProvenanceService get()
    {
        return ServiceRegistry.get().getService(ProvenanceService.class);
    }

    static void setInstance(ProvenanceService impl)
    {
        ServiceRegistry.get().registerService(ProvenanceService.class, impl);
    }

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

    /**
     * Delete provenance for assay result rows.
     */
    void deleteAssayResultProvenance(@NotNull SQLFragment sqlFragment);

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
    List<? extends ExpRun> getRuns(Set<String> lsids);

    /**
     * Get the ExpRun referenced by the set of LSIDs
     */
    Map<String, Set<ExpRun>> getRunsByLsid(Set<String> lsids);

}
