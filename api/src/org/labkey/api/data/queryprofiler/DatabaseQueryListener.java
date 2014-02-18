package org.labkey.api.data.queryprofiler;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.User;

/**
 * Gets invoked when a query that matches its registered substring is run against the underlying database.
 *
 * User: jeckels
 * Date: 2/13/14
 */
public interface DatabaseQueryListener<T>
{
    /** Called when a query that matches the pattern is actually run against the database */
    public void queryInvoked(DbScope scope, String sql, User user, Container container, @Nullable T environment);

    /** @return a custom context, which will be provided if and when the queryInvoked() method is called. This will be called
     * from the originating thread (not an asychronous thread that might actually be running the query), so it can
     * gather information from ThreadLocals if needed */
    @Nullable
    public T getEnvironment();
}
