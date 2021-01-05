package org.labkey.api.data;

import java.sql.SQLException;

/**
 * <p>A {@link ResultsFactory} wrapper that stashes the {@link Results} on first reference and ensures the Results gets
 * closed. Always use try-with-resources when creating an instance of this class.</p>
 *
 * <p>This class is useful for cases where code needs to inspect Results before invoking a class that takes a ResultsFactory.
 * For example, code may want to short-circuit in the case of zero rows or use the Results metadata to configure an
 * ExcelWriter or TSVGridWriter. Proper use of this class will ensure that the Results is closed in all cases (successful
 * render, short-circuit, or error).</p>
 */
public class StashingResultsFactory implements ResultsFactory, AutoCloseable
{
    private final ResultsFactory _factory;

    private Results _results = null;

    public StashingResultsFactory(ResultsFactory factory)
    {
        _factory = factory;
    }

    @Override
    public Results get() throws Exception
    {
        if (null == _results)
            _results = _factory.get();

        return _results;
    }

    @Override
    public void close() throws SQLException
    {
        if (_results != null && !_results.isClosed())
            _results.close();
    }
}
