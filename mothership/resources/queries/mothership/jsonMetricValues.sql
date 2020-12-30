WITH metrics AS (
    SELECT
        NULL AS Key,
        JsonMetrics AS Value,
        CAST('object' AS VARCHAR) AS type,
        NULL as DisplayKey,
        NULL as SelectKey,
        ServerSessionId
    FROM mothership.ServerSessions

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
       CASE WHEN Type = 'string' THEN Value END AS StringValue,
       CAST(CASE WHEN Type = 'number' THEN Value END AS DECIMAL) AS NumberValue,
       CAST(CASE WHEN Type = 'boolean' THEN Value END AS BOOLEAN) AS BooleanValue,
       CASE WHEN Type = 'object' THEN Value END AS ObjectValue,
       Type,
       ServerSessionId
FROM
    metrics