package org.labkey.query.metadata.client;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: Oct 6, 2009
 */
public class MetadataUnavailableException extends Exception implements Serializable
{
    public MetadataUnavailableException()
    {
        super();
    }

    public MetadataUnavailableException(String message)
    {
        super(message);
    }
}
