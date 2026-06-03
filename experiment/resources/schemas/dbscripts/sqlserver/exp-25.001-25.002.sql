/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- We don't expect storage column names this large, but this allows us to remove some truncation code
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN StorageColumnName NVARCHAR(255);
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN mvIndicatorStorageColumnName NVARCHAR(275);
