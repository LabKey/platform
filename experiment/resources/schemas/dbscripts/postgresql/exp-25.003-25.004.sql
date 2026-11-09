/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--Issue 53478: DataInput Role can be a DataClass name, which has a max length of 200 characters
ALTER TABLE exp.DataInput ALTER COLUMN Role TYPE VARCHAR(200);