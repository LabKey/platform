/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

/* audit-0.00-10.20.sql */

CREATE SCHEMA audit;

CREATE TABLE audit.AuditLog
(
    RowId SERIAL,
    Key1 VARCHAR(200),
    Key2 VARCHAR(200),
    Key3 VARCHAR(200),
    IntKey1 INT NOT NULL,
    IntKey2 INT NOT NULL,
    IntKey3 INT NOT NULL,
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
CREATE INDEX IX_Audit_EventType ON audit.AuditLog(EventType);

/* audit-11.20-11.30.sql */

ALTER TABLE audit.auditlog
    ALTER COLUMN key1 TYPE VARCHAR(1000),
    ALTER COLUMN key2 TYPE VARCHAR(1000),
    ALTER COLUMN key3 TYPE VARCHAR(1000);

/* audit-11.30-12.10.sql */

ALTER TABLE audit.auditlog ALTER intkey1 DROP NOT NULL;
ALTER TABLE audit.auditlog ALTER intkey2 DROP NOT NULL;
ALTER TABLE audit.auditlog ALTER intkey3 DROP NOT NULL;
