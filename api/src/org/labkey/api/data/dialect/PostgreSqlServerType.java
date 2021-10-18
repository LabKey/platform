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
        boolean supportsGroupConcat()
        {
            return true;
        }

        @Override
        boolean supportsSpecialMetadataQueries()
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
        boolean supportsGroupConcat()
        {
            return false;
        }

        @Override
        boolean supportsSpecialMetadataQueries()
        {
            return false;
        }
    };

    abstract boolean shouldTest();
    abstract boolean supportsGroupConcat();
    abstract boolean supportsSpecialMetadataQueries();

    public static PostgreSqlServerType getFromParameterStatuses(Map<String, String> parameterStatuses)
    {
        return "LabKey Server".equals(parameterStatuses.get("server_name")) ? LabKey : PostgreSQL;
    }
}
