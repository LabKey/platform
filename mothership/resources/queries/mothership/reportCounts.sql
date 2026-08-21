/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
    ServerSessionId,
    SUM(NumberValue) AS ReportCount,
    Key
FROM recentJsonMetricValues
WHERE DisplayKey LIKE 'modules.Study.reportCountsByType.%'
GROUP BY ServerSessionId, Key
    PIVOT ReportCount BY Key