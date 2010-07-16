/*
 * Copyright (c) 2008 LabKey Corporation
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
/* query-8.20-8.21.sql */

/* query-8.20-8.21.sql */

DELETE FROM query.dbuserschema WHERE dbschemaname IS NULL;

ALTER TABLE query.dbuserschema ALTER COLUMN dbschemaname SET NOT NULL;

/* query-8.21-8.22.sql */

CREATE TABLE query.QuerySnapshotDef (
    RowId SERIAL NOT NULL,
    QueryDefId INT NULL,

    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy int NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy int NULL,
    Container ENTITYID NOT NULL,
    Schema VARCHAR(50) NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Columns TEXT,
    Filter TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

/* query-8.22-8.23.sql */

ALTER TABLE query.QueryDef ALTER COLUMN Name TYPE VARCHAR(200);
ALTER TABLE query.QuerySnapshotDef ALTER COLUMN Name TYPE VARCHAR(200);

ALTER TABLE query.QuerySnapshotDef ADD COLUMN LastUpdated TIMESTAMP NULL;
ALTER TABLE query.QuerySnapshotDef ADD COLUMN NextUpdate TIMESTAMP NULL;
ALTER TABLE query.QuerySnapshotDef ADD COLUMN UpdateDelay INT DEFAULT 0;

/* query-8.23-8.24.sql */

ALTER TABLE query.QuerySnapshotDef ADD COLUMN ViewName VARCHAR(50) NULL;

/* query-8.24-8.25.sql */

ALTER TABLE query.QuerySnapshotDef ADD COLUMN QueryTableName VARCHAR(200) NULL;
ALTER TABLE query.QuerySnapshotDef DROP COLUMN ViewName;