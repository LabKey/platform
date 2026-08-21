/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Switch to always store the lower case version of the path. First, ensure we only have one row per path, regardless
-- of its casing
DELETE FROM core.ContainerAliases
WHERE Path NOT IN (SELECT MIN(Path)
                    FROM core.ContainerAliases
                    GROUP BY LOWER(Path));

UPDATE core.ContainerAliases SET Path = LOWER(Path);

-- Switch to using RowId as the FK to core.containers
ALTER TABLE core.ContainerAliases
    ADD ContainerRowId INT;
GO

UPDATE core.ContainerAliases
SET ContainerRowId = (SELECT RowId
                      FROM core.containers
                      WHERE core.containers.EntityId = core.ContainerAliases.ContainerId);

ALTER TABLE core.ContainerAliases
    DROP CONSTRAINT FK_ContainerAliases_Containers;
GO

ALTER TABLE core.ContainerAliases
    DROP COLUMN ContainerId;

ALTER TABLE core.ContainerAliases
    ADD CONSTRAINT FK_ContainerRowId FOREIGN KEY (ContainerRowId)
        REFERENCES core.containers (RowId);

CREATE INDEX idx_ContainerAliases_ContainerRowId ON core.ContainerAliases (ContainerRowId);