SELECT
    AssayTypes,
    AssayNames,
    StandardDesigns,
    SpecialtyDesigns,
    SpecialtyAssayTypesInUse,
    COALESCE(x.ServerSessionId, y.ServerSessionId) AS ServerSessionId

FROM (
         SELECT
             COUNT(DISTINCT DisplayKey) AS AssayTypes,
             GROUP_CONCAT(Key, ', ') AS AssayNames,
             ServerSessionId
         FROM
             recentJsonMetricValues
         WHERE DisplayKey LIKE 'modules.Experiment.assay.%' AND DisplayKey NOT LIKE 'modules.Experiment.assay.%.%' AND ServerSessionId IN (SELECT MostRecentSession FROM ServerInstallations)
         GROUP BY ServerSessionId
     ) x
         FULL OUTER JOIN
     (SELECT
          SUM(CASE WHEN DisplayKey = 'modules.Experiment.assay.General.protocolCount' THEN NumberValue ELSE 0 END) AS StandardDesigns,
          SUM(CASE WHEN DisplayKey = 'modules.Experiment.assay.General.protocolCount' THEN 0 ELSE NumberValue END) AS SpecialtyDesigns,
          SUM(CASE WHEN DisplayKey != 'modules.Experiment.assay.General.protocolCount' AND NumberValue > 0 THEN 1 ELSE 0 END) AS SpecialtyAssayTypesInUse,
          ServerSessionId
      FROM
          recentJsonMetricValues
      WHERE DisplayKey LIKE 'modules.Experiment.assay.%.protocolCount' AND ServerSessionId IN (SELECT MostRecentSession FROM ServerInstallations)
      GROUP BY ServerSessionId
     ) y ON x.ServerSessionId = y.ServerSessionId