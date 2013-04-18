package org.labkey.search.model;

/**
 * User: adam
 * Date: 4/17/13
 * Time: 7:47 AM
 */
public class IndexManagerClosedException extends RuntimeException
{
    public IndexManagerClosedException()
    {
        super("Index manager has been closed");
    }
}
