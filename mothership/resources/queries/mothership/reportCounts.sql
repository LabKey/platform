SELECT
    ServerSessionId,
    SUM(NumberValue) AS ReportCount,
    Key
FROM recentJsonMetricValues
WHERE DisplayKey LIKE 'modules.Study.reportCountsByType.%'
GROUP BY ServerSessionId, Key
    PIVOT ReportCount BY Key