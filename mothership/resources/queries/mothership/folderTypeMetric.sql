SELECT
    JsonMetrics as Study,
    JsonMetrics as Assay,
    JsonMetrics as Collaboration,
    JsonMetrics as MicroArray,
    JsonMetrics as TargetedMS,
    JsonMetrics as NAb,
    JsonMetrics as Flow,
    JsonMetrics
FROM ServerSessions
WHERE JsonMetrics IS NOT NULL AND JsonMetrics <> ' '

