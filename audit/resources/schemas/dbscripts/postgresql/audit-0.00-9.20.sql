/*
 * Copyright (c) 2010 LabKey Corporation
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

/* audit-0.00-2.30.sql */

CREATE SCHEMA audit;

CREATE TABLE audit.AuditLog
(
    RowId SERIAL,
    Key1 VARCHAR(200),
    Key2 VARCHAR(200),
    Key3 VARCHAR(200),
    IntKey1 INT NULL,
    IntKey2 INT NULL,
    IntKey3 INT NULL,
    Comment VARCHAR(500),
    EventType VARCHAR(64),
    CreatedBy USERID,
    Created TIMESTAMP,
    ContainerId ENTITYID,
    EntityId ENTITYID NULL,
    Lsid LSIDtype,
    ProjectId ENTITYID,

    CONSTRAINT PK_AuditLog PRIMARY KEY (RowId)
);

INSERT INTO audit.AuditLog (IntKey1, Created, Comment, EventType)
    (SELECT UserId, Date, Message, 'UserAuditEvent' FROM core.UserHistory);

/* audit-0.00-8.30.sql */

ALTER TABLE audit.AuditLog
    ADD Impersonator USERID NULL;

ALTER TABLE audit.AuditLog
    RENAME Impersonator TO ImpersonatedBy;

UPDATE audit.AuditLog SET IntKey1 = '0' WHERE IntKey1 IS NULL;
UPDATE audit.AuditLog SET IntKey2 = '0' WHERE IntKey2 IS NULL;
UPDATE audit.AuditLog SET IntKey3 = '0' WHERE IntKey3 IS NULL;
UPDATE audit.AuditLog SET CreatedBy = '0' WHERE CreatedBy IS NULL;

ALTER TABLE audit.AuditLog ALTER COLUMN IntKey1 SET NOT NULL;
ALTER TABLE audit.AuditLog ALTER COLUMN IntKey2 SET NOT NULL;
ALTER TABLE audit.AuditLog ALTER COLUMN IntKey3 SET NOT NULL;
ALTER TABLE audit.AuditLog ALTER COLUMN CreatedBy SET NOT NULL;

/* audit-9.10-9.20.sql */

CREATE INDEX IX_Audit_Container ON audit.AuditLog(ContainerId);
CREATE INDEX IX_Audit_EventType ON audit.AuditLog(EventType);