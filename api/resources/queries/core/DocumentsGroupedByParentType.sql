SELECT ParentType, COUNT(*) AS "Count"
FROM Documents
GROUP BY ParentType
ORDER BY ParentType
