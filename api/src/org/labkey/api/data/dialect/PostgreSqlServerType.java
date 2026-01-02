package org.labkey.api.data.dialect;

import java.util.Map;

public enum PostgreSqlServerType
{
    PostgreSQL()
    {
        @Override
        boolean shouldTest()
        {
            return true;
        }

        @Override
        public boolean supportsGroupConcat()
        {
            return true;
        }

        @Override
        public boolean supportsSpecialMetadataQueries()
        {
            return true;
        }
    },
    LabKey
    {
        @Override
        boolean shouldTest()
        {
            return false;
        }

        @Override
        public boolean supportsGroupConcat()
        {
            return false;
        }

        @Override
        public boolean supportsSpecialMetadataQueries()
        {
            return false;
        }
    };

    abstract boolean shouldTest();
    public abstract boolean supportsGroupConcat();
    public abstract boolean supportsSpecialMetadataQueries();

    public static PostgreSqlServerType getFromParameterStatuses(Map<String, String> parameterStatuses)
    {
        return "LabKey Server".equals(parameterStatuses.get("server_name")) ? LabKey : PostgreSQL;
    }
}
