package org.labkey.api.exp.api;

import org.labkey.api.exp.Identifiable;

import java.util.Set;

public record ExpLineageStream(
    Set<Identifiable> seeds,
    Set<ExpLineage.Edge> edges,
    Set<Integer> dataIds,
    Set<Integer> materialIds,
    Set<Integer> runIds,
    Set<String> objectLsids
)
{
}
