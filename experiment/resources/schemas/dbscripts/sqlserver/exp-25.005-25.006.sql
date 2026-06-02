/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Issue 53561 - store table names at least as long as the maximum identifier length on supported primary databases
ALTER TABLE exp.DomainDescriptor ALTER COLUMN StorageTableName NVARCHAR(150);
