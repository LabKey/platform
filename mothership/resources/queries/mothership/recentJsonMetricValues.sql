WITH metrics AS (
    SELECT
        CAST(NULL AS VARCHAR) AS Key,
        JsonMetrics AS Value,
        CAST('object' AS VARCHAR) AS type,
        CAST(NULL AS VARCHAR) AS DisplayKey,
        NULL as SelectKey,
        ServerSessionId
    -- Include data from all servers active in the last 2 weeks, plus any server that lasted more than a day in the last two years
    FROM mothership.ServerSessions WHERE ServerSessionId IN (SELECT MostRecentSession FROM mothership.ServerInstallation WHERE (LastPing >= timestampadd('SQL_TSI_DAY', -14, curdate())) OR ((LastPing >= timestampadd('SQL_TSI_YEAR', -2, curdate())) AND DaysActive > 1))

    UNION ALL

    SELECT
        jsonb_object_keys(value) AS Key,
        json_op(value, '->', jsonb_object_keys(value)) AS Value,
        jsonb_typeof(json_op(value, '->', jsonb_object_keys(value))) AS Type,
        COALESCE(m.DisplayKey || '.', '') || jsonb_object_keys(value) AS DisplayKey,
        COALESCE(m.SelectKey || ',', '') || '"' || jsonb_object_keys(value) || '"' AS SelectKey,
        ServerSessionId
    FROM
        metrics m
    WHERE
            jsonb_typeof(value) = 'object'
)

SELECT
       Key,
       DisplayKey,
       '{' || SelectKey || '}' AS SelectKey,
       Value,
       -- Need to strip quotes from the JSON string value
       CAST(CASE WHEN Type = 'string' THEN SUBSTRING(CAST(Value AS VARCHAR), 2, LENGTH(CAST(Value AS VARCHAR)) - 2) END AS VARCHAR) AS StringValue,
       CAST(CASE WHEN Type = 'number' THEN Value END AS DECIMAL) AS NumberValue,
       CAST(CASE WHEN Type = 'boolean' THEN Value END AS BOOLEAN) AS BooleanValue,
       CASE WHEN Type = 'object' THEN Value END AS ObjectValue,
       Type,
       ServerSessionId
FROM
    metrics