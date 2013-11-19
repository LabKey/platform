/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

/* audit-0.00-8.30.sql */

-- Table used by Audit module
CREATE SCHEMA audit;
GO

CREATE TABLE audit.AuditLog
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Key1 NVARCHAR(200),
    Key2 NVARCHAR(200),
    Key3 NVARCHAR(200),
    IntKey1 INT NOT NULL,
    IntKey2 INT NOT NULL,
    IntKey3 INT NOT NULL,
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

/* audit-9.10-9.20.sql */

CREATE INDEX IX_Audit_Container ON audit.AuditLog(ContainerId);
CREATE INDEX IX_Audit_EventType ON audit.AuditLog(EventType);
