/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

CREATE TABLE audit.AuditLog
(
    RowId SERIAL,
    Key1 VARCHAR(1000) NULL,
    Key2 VARCHAR(1000) NULL,
    Key3 VARCHAR(1000) NULL,
    IntKey1 INT NULL,
    IntKey2 INT NULL,
    IntKey3 INT NULL,
    Comment VARCHAR(500),
    EventType VARCHAR(64),
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP,
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
CREATE INDEX IX_AuditLog_EventType_Created ON audit.AuditLog(EventType, Created);
CLUSTER audit.AuditLog USING IX_AuditLog_EventType_Created;
