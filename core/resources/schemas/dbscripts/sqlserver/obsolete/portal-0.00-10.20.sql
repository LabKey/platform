/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

CREATE SCHEMA portal;
GO

CREATE TABLE portal.PortalWebParts
(
    PageId ENTITYID NOT NULL,
    [Index] INT NOT NULL,
    Name VARCHAR(64),
    Location VARCHAR(16),    -- 'body', 'left', 'right'
    Properties TEXT,    -- url encoded properties
    Permanent BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PortalWebParts PRIMARY KEY (PageId, [Index])
);

/* portal-10.10-10.20.sql */

ALTER TABLE Portal.PortalWebParts
    ADD RowId INT IDENTITY(1, 1) NOT NULL;

ALTER TABLE Portal.PortalWebParts
    DROP CONSTRAINT PK_PortalWebParts;

ALTER TABLE Portal.PortalWebParts
    ADD CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId);

