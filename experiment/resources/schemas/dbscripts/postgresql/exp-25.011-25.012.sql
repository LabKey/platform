/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- These tables have FKs to exp.Data without corresponding indices. Add indices to speed up exp.Data delete.
CREATE INDEX IX_DataInput_DataId ON exp.DataInput (DataId);
CREATE INDEX IX_DataAncestors_RowId ON exp.DataAncestors (RowId);
