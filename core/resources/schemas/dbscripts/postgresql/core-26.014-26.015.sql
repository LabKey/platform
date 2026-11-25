/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- Set an explicit type for Python report engines that extension previously resolved.
UPDATE core.ReportEngines SET Type = 'Python'
WHERE Type IN ('External', 'Perl') AND Configuration IS NOT NULL
  AND EXISTS (SELECT 1 FROM unnest(string_to_array(Configuration::jsonb->>'extensions', ',')) AS ext WHERE lower(ext) = 'py');

-- Transform scripts get their own engine context, starting with each folder's current pipeline engine
INSERT INTO core.ReportEngineMap (EngineId, Container, EngineContext)
SELECT EngineId, Container, 'transform' FROM core.ReportEngineMap WHERE EngineContext = 'pipeline';

-- Site defaults move from each definition's configuration to root container mappings in every context.
INSERT INTO core.ReportEngineMap (EngineId, Container, EngineContext)
SELECT d.RowId, root.EntityId, ctx.Name
FROM (SELECT DISTINCT ON (Type, CASE WHEN Type = 'External' THEN RowId ELSE 0 END) RowId
      FROM core.ReportEngines
      WHERE Configuration IS NOT NULL AND Configuration::jsonb->>'default' = 'true'
      ORDER BY Type, CASE WHEN Type = 'External' THEN RowId ELSE 0 END, RowId) d
CROSS JOIN (SELECT EntityId FROM core.Containers WHERE Parent IS NULL) root
CROSS JOIN (VALUES ('report'), ('transform'), ('pipeline')) AS ctx(Name)
ON CONFLICT DO NOTHING;
