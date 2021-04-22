package org.labkey.api.data.dialect;

import java.util.Map;

public enum PostgreSqlServerType
{
    PostgreSQL,
    LabKey;

    public static PostgreSqlServerType getFromParameterStatuses(Map<String, String> parameterStatuses)
    {
        return "LabKey Server".equals(parameterStatuses.get("server_name")) ? LabKey : PostgreSQL;
    }
}
