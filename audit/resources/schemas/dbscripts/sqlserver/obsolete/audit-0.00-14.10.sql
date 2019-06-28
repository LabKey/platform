/*
 * Copyright (c) 2018-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* audit-0.00-12.10.sql */

CREATE SCHEMA audit;
GO

CREATE TABLE audit.AuditLog
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Key1 NVARCHAR(1000) NULL,
    Key2 NVARCHAR(1000) NULL,
    Key3 NVARCHAR(1000) NULL,
    IntKey1 INT NULL,
    IntKey2 INT NULL,
    IntKey3 INT NULL,
    Comment NVARCHAR(500),
    EventType NVARCHAR(64),
    CreatedBy USERID NOT NULL,
    Created DATETIME,
    ContainerId ENTITYID NOT NULL,
    EntityId ENTITYID NULL,
    Lsid LSIDtype,
    ProjectId ENTITYID,
    ImpersonatedBy USERID NULL,

    CONSTRAINT PK_AuditLog PRIMARY KEY (RowId)
);
CREATE INDEX IX_Audit_Container ON audit.AuditLog(ContainerId);

/* audit-12.20-12.30.sql */

CREATE INDEX IX_AuditLog_IntKey1 ON audit.AuditLog(IntKey1);
ALTER TABLE audit.AuditLog DROP CONSTRAINT PK_AuditLog;
CREATE CLUSTERED INDEX IX_AuditLog_EventType_Created ON audit.AuditLog(EventType, Created DESC);
-- NONCLUSTERED
ALTER TABLE audit.AuditLog ADD CONSTRAINT PK_AuditLog PRIMARY KEY (RowId);
