/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
ALTER TABLE mothership.ExceptionStackTrace ADD COLUMN GitHubIssue INT;

ALTER TABLE mothership.ExceptionStackTrace RENAME COLUMN BugNumber TO LabKeyIssue;