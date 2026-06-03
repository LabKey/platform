/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- persist deferred upgrade methods, potentially across server sessions
CREATE TABLE core.UpgradeSteps
(
    RowId INT IDENTITY(1,1) NOT NULL,
    ModuleName NVARCHAR(255) NOT NULL,
    Script NVARCHAR(255) NOT NULL,
    MethodName NVARCHAR(255) NOT NULL,
    Created DATETIME NOT NULL,
    Executed DATETIME NULL,

    CONSTRAINT PK_UpgradeSteps PRIMARY KEY (RowId)
);
