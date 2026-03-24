package org.labkey.search.model;

/**
 * Thrown when a Lucene index commit fails. Propagates to the outer indexer loop so that backoff and retry
 * are handled there, consistent with the pattern used by {@link DavCrawler}.
 */
public class IndexCommitException extends RuntimeException
{
    IndexCommitException(Throwable cause)
    {
        super(cause);
    }
}
