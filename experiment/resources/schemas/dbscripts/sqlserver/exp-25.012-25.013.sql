/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Not needed since it's redundant with exp.DataInput's PK
DROP INDEX IX_DataInput_DataId ON exp.DataInput;

CREATE INDEX IX_MaterialAncestors_RowId ON exp.MaterialAncestors (RowId);
