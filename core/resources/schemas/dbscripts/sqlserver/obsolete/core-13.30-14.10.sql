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

/* core-13.32-13.33.sql */

EXEC core.executeJavaUpgradeCode 'ensureCoreUserPropertyDescriptorScales';

/* core-13.33-13.34.sql */

CREATE TABLE core.ShortURL
(
    RowId INT IDENTITY(1, 1),
    EntityId ENTITYID NOT NULL,
    ShortURL NVARCHAR(255) NOT NULL,
    FullURL NVARCHAR(4000) NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    CONSTRAINT PK_ShortURL PRIMARY KEY (RowId),
    CONSTRAINT UQ_ShortURL_EntityId UNIQUE (EntityId),
    CONSTRAINT UQ_ShortURL_ShortURL UNIQUE (ShortURL)
);