/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data;

import java.io.IOException;
import java.sql.SQLException;

/**
 * <p>A {@link ResultsFactory} wrapper that stashes the {@link Results} on first reference and ensures the Results gets
 * closed. Always use try-with-resources when creating an instance of this class.</p>
 *
 * <p>Use this class only if you must; most code paths shouldn't need to inspect the Results. This wrapper is useful for
 * cases where you must inspect Results before configuring an ExcelWriter or TSVGridWriter, for example, to short-circuit
 * in the case of zero rows or use the Results metadata to configure the writer. Proper use of this class will ensure
 * that Results is closed in all cases (successful render, short-circuit, or error).</p>
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
    public Results get() throws IOException, SQLException
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
