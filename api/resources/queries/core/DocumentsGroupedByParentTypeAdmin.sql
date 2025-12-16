-- Identical to DocumentsGroupedByParentType, but this query's .query.xml provides an admin-console-specific Count URL
SELECT ParentType, COUNT(*) AS "Count"
FROM Documents
GROUP BY ParentType
ORDER BY ParentType
