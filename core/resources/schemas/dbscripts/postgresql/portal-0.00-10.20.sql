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
 
/* portal-0.00-8.30.sql */

CREATE SCHEMA portal;

CREATE TABLE portal.PortalWebParts
(
    PageId ENTITYID NOT NULL,
    Index INT NOT NULL,
    Name VARCHAR(64),
    Location VARCHAR(16),    -- 'body', 'left', 'right'
    Properties TEXT,    -- url encoded properties
    Permanent Boolean NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PortalWebParts PRIMARY KEY (PageId, Index)
);

/* portal-10.10-10.20.sql */

ALTER TABLE Portal.PortalWebParts
    ADD COLUMN RowId SERIAL NOT NULL,
    DROP CONSTRAINT PK_PortalWebParts,
    ADD CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId);
