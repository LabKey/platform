package org.labkey.api.search;

/**
 * Defines a listener interface for events related to the completion of search indexing startup/initialization.
 * Implementations of this interface can define specific actions to be executed once the search startup process
 * is completed during the application's startup or initialization phase.
 */
public interface SearchStartupListener
{
    String getName();

    void indexStartupComplete();
}
