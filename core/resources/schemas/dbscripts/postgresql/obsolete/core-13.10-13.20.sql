/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

/* core-13.10-13.11.sql */

CREATE TABLE core.DbSequences
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(500) NOT NULL,
    Id INTEGER NOT NULL,
    Value BIGINT NOT NULL,

    CONSTRAINT PK_DbSequences PRIMARY KEY (RowId),
    CONSTRAINT UQ_DbSequences_Container_Name_Id UNIQUE (Container, Name, Id)
);

/* core-13.13-13.14.sql */

SELECT core.fn_dropifexists ('PortalPages', 'core', 'DEFAULT', 'Index');
SELECT core.executeJavaUpgradeCode('setPortalPageUniqueIndexes');
ALTER TABLE core.PortalPages ADD CONSTRAINT UQ_PortalPage UNIQUE (Container, Index);

/* core-13.14-13.15.sql */

SELECT core.executeJavaUpgradeCode('populateWorkbookSortOrderAndName');
