/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--GitHub Issue #652: materialinput Role can be a field name, which has a max length of 200 characters
ALTER TABLE exp.MaterialInput ALTER COLUMN Role TYPE VARCHAR(200);