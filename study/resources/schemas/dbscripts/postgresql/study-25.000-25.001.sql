/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

ALTER TABLE study.DataSet ADD COLUMN SourceQueryName VARCHAR(200);
ALTER TABLE study.DataSet ADD COLUMN SourceQuerySchema VARCHAR(200);
ALTER TABLE study.DataSet ADD COLUMN SourceQueryContainer ENTITYID;