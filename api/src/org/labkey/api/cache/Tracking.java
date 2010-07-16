package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:44:59 AM
 */
public interface Tracking
{
    String getDebugName();

    Stats getStats();

    Stats getTransactionStats();
}
