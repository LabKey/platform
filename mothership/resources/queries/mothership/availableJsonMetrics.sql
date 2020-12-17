SELECT
    DISTINCT DisplayKey AS JsonKey, '{' || SelectKey || '}' AS SelectKey, Type
FROM
    jsonMetricValues