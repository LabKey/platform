/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
DELETE FROM core.Members WHERE GroupId NOT IN (SELECT UserId FROM core.Principals WHERE Type IN ('g', 'm'));
ALTER TABLE core.Members ADD CONSTRAINT FK_Members_Principals FOREIGN KEY (GroupId) REFERENCES core.Principals (UserId);
CREATE INDEX IX_Members_GroupId ON core.Members(GroupId);
