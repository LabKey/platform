/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

/* query-14.20-14.21.sql */

CREATE TABLE query.OlapDef
(
    RowId SERIAL NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Module VARCHAR(255) NOT NULL,
    Definition TEXT NOT NULL,

    CONSTRAINT PK_OlapDef PRIMARY KEY (RowId),
    CONSTRAINT UQ_OlapDef UNIQUE (Container, Name)
);