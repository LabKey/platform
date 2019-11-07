package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
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
    String PROVENANCE_INPUT_PROPERTY = "prov:objectInputs";

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

    void addProvenance(Container container, ExpProtocolApplication app, Map<String, Set<String>> outputMap);

    List<Map<Integer, Pair<String,String>>> getProvenance(int protocolAppId);
}
