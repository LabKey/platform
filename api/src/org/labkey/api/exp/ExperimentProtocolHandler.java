package org.labkey.api.exp;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.QueryRowReference;

/**
 * Provides some basic recognition for protocols of a particular type.
 */
public interface ExperimentProtocolHandler extends Handler<ExpProtocol>
{
    /**
     * Get a query reference for the protocol type.
     */
    public @Nullable QueryRowReference getQueryRowReference(ExpProtocol protocol);

    /**
     * Get a query reference for the run of the protocol type.
     */
    public @Nullable QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpRun run);

    /**
     * Get a query reference for the protocol application of the protocol type.
     */
    public @Nullable QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpProtocolApplication app);
}
