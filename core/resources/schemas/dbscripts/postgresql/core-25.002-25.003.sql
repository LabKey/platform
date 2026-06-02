/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- persist deferred upgrade methods, potentially across server sessions
CREATE TABLE core.UpgradeSteps
(
    RowId SERIAL,
    ModuleName VARCHAR(255) NOT NULL,
    Script VARCHAR(255) NOT NULL,
    MethodName VARCHAR(255) NOT NULL,
    Created TIMESTAMP NOT NULL,
    Executed TIMESTAMP NULL,

    CONSTRAINT PK_UpgradeSteps PRIMARY KEY (RowId)
);
