package org.labkey.api.exp.api;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
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
    static ProvenanceService get()
    {
        return ServiceRegistry.get().getService(ProvenanceService.class);
    }

    static void setInstance(ProvenanceService impl)
    {
        ServiceRegistry.get().registerService(ProvenanceService.class, impl);
    }

    Map<Integer, Set<String>> addProvenanceInputs(ExpProtocolApplication app, Set<String> inputLSIDs);

    Map<Integer, Set<String>> addProvenanceOutputs(ExpProtocolApplication app, Set<String> outputLSIDs);

    void addProvenanceMapping(ExpProtocolApplication app, List<String> inputLSIDs, List<String> outputLSIDs);

    void addProvenance(ExpRun run, List<String> runInputLSIDs, List<Pair<List<String>, List<String>>> outputMap);

    Pair<List<String>, List<String>> getProvenance(ExpProtocolApplication app);

    DbSchema getDbSchema();

    TableInfo getTinfoProtocolApplicationObjectMap();

}
