/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

ALTER TABLE study.DataSet ADD SourceQueryName NVARCHAR(200);
ALTER TABLE study.DataSet ADD SourceQuerySchema NVARCHAR(200);
ALTER TABLE study.DataSet ADD SourceQueryContainer ENTITYID;