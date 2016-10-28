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

/* filecontent-0.00-12.10.sql */

CREATE SCHEMA filecontent;

CREATE TABLE filecontent.FileRoots
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Path VARCHAR(255),
    Type VARCHAR(50),
    Properties TEXT,

    Enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UseDefault BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_FileRoots PRIMARY KEY (RowId)
);

/* filecontent-14.10-14.20.sql */

DELETE FROM prop.properties WHERE name = 'experimentalFeature.dragDropUpload';

DELETE FROM filecontent.FileRoots WHERE RowId NOT IN (SELECT MIN(RowId) FROM filecontent.FileRoots GROUP BY Container);
ALTER TABLE filecontent.FileRoots ADD CONSTRAINT Container_Type_Key UNIQUE (Container, Type);