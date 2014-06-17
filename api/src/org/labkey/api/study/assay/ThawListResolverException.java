package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentException;

/**
 * User: tgaluhn
 * Date: 6/16/2014
 *
 * Subclass to enable marshalling of all row errors instead of halting import on first error.
 *
 */
public class ThawListResolverException extends ExperimentException
{
    public ThawListResolverException(String message)
    {
        super(message);
    }
}
