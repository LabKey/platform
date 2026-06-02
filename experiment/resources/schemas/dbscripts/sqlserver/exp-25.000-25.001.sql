/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- remove limit on stringvalue field
ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue NVARCHAR(MAX) NULL;
