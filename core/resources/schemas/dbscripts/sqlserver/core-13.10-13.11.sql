/*
 * Copyright (c) 2013 LabKey Corporation
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

CREATE TABLE core.DbSequences
(
    RowId INT IDENTITY,
    Container ENTITYID NOT NULL,
    Name VARCHAR(500) NOT NULL,
    Id INTEGER NOT NULL,
    Value BIGINT NOT NULL,

    CONSTRAINT PK_DbSequences PRIMARY KEY (RowId),
    CONSTRAINT UQ_DbSequences_Container_Name_Id UNIQUE (Container, Name, Id)
);
