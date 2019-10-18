package org.labkey.api.exp.api;

import org.labkey.api.util.Pair;

import java.util.List;

public interface ProvenanceService
{
    void addProvenanceInputs(ExpProtocolApplication app, List<String> inputLSIDs);

    void addProvenanceOutputs(ExpProtocolApplication app, List<String> outputLSIDs);

    void addProvenanceMapping(ExpProtocolApplication app, List<String> inputLSIDs, List<String> outputLSIDs);

    void addProvenance(ExpRun run, List<String> runInputLSIDs, List<Pair<List<String>, List<String>>> outputMap);

}
