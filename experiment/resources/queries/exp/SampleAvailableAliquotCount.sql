SELECT
CAST((CASE WHEN c.AliquotsCount IS NULL THEN 0 ELSE c.AliquotsCount END) AS INTEGER) as AvailableAliquotsCount,
ma.lsid
FROM
     exp.materials ma LEFT JOIN
(
    SELECT m.RootMaterialLSID as lsid, COUNT(*) AS AliquotsCount
    FROM exp.materials m JOIN core.datastates[ContainerFilter='CurrentPlusProjectAndShared'] s
    ON m.SampleState = s.rowid
    WHERE m.RootMaterialLSID IS NOT NULL AND s.StateType = 'Available'
    GROUP BY RootMaterialLSID
) c
ON
ma.lsid = c.lsid
WHERE ma.RootMaterialLSID IS NULL;