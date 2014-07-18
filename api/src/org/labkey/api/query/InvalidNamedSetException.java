package org.labkey.api.query;

import org.labkey.api.util.ExpectedException;
import org.labkey.api.view.NotFoundException;

/**
 * User: tgaluhn
 * Date: 7/15/2014
 */

/**
 *  Thrown if a requested named member set isn't found in the cache in QueryService. It either never existed, or has expired
 *  from the cache. As the cache miss due to expiration is considered normal operation, mmplements ExpectedException
 *  so we don't log to the console in API calls.
 */
public class InvalidNamedSetException extends NotFoundException implements ExpectedException
{
    public InvalidNamedSetException(String string)
    {
        super(string);
    }
}
