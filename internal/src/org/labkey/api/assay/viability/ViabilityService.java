package org.labkey.api.assay.viability;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;

/**
 * User: kevink
 * Date: 6/23/14
 */
public interface ViabilityService
{
    /**
     * Update specimen aggregates stored on the viability.results table for the assay protocol.
     */
    void updateSpecimenAggregates(User user, Container c, AssayProvider provider, ExpProtocol protocol, @Nullable ExpRun run);
}
