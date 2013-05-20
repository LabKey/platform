package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.UserSchema;

/**
 * User: jeckels
 * Date: 5/15/13
 */
public interface QueryReportDataSource extends ReportDataSource
{
    public QueryDefinition getQueryDefinition();

    public String getLabKeySQL();

    @NotNull
    public UserSchema getSchema();

    public static class DummyQueryDataSource implements QueryReportDataSource
    {
        private static final String DUMMY_SCHEMA_TABLE = "mySchema.myTable";

        @Override
        public QueryDefinition getQueryDefinition()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getLabKeySQL()
        {
            return DUMMY_SCHEMA_TABLE;
        }

        @NotNull
        @Override
        public UserSchema getSchema()
        {
            throw new UnsupportedOperationException();
        }
    }
}
