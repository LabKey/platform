/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- GH Issue 1563: supports the :withCounter max-counter probe, LOWER(Name) LIKE 'prefix%' within one type
CREATE INDEX IX_Material_MaterialSourceId_LowerName ON exp.Material (MaterialSourceId, LOWER(Name) text_pattern_ops) WHERE MaterialSourceId IS NOT NULL;
CREATE INDEX IX_Data_ClassId_LowerName ON exp.Data (ClassId, LOWER(Name) text_pattern_ops) WHERE ClassId IS NOT NULL;
