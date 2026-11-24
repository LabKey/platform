/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- Any engine other than R or Jupyter with a "py" extension resolved to Python, including Perl definitions with a changed extension
UPDATE core.ReportEngines SET Type = 'Python'
WHERE Type IN ('External', 'Perl') AND Configuration IS NOT NULL
  AND EXISTS (SELECT 1 FROM unnest(string_to_array(Configuration::jsonb->>'extensions', ',')) AS ext WHERE lower(ext) = 'py');
