/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

EXEC core.fn_dropifexists 'portalwebparts', 'core', 'CONSTRAINT', 'FK_PortalWebPartPages';
EXEC core.fn_dropifexists 'portalpages', 'core', 'CONSTRAINT', 'PK_PortalPages';
EXEC core.fn_dropifexists 'portalpages', 'core', 'CONSTRAINT', 'UQ_PortalPage';

ALTER TABLE core.PortalPages ADD RowId INT IDENTITY(1,1) NOT NULL;
ALTER TABLE core.PortalWebParts ADD PortalPageId INT;
GO

ALTER TABLE core.PortalPages ADD CONSTRAINT PK_PortalPages PRIMARY KEY (RowId);
UPDATE core.portalwebparts SET PortalPageId = page.RowId
    FROM core.PortalPages page, core.PortalWebParts web
    WHERE web.PageId = page.PageId;
GO

ALTER TABLE core.PortalWebParts DROP COLUMN PageId;
ALTER TABLE core.PortalWebParts ALTER COLUMN PortalPageId INT NOT NULL;
ALTER TABLE core.PortalWebParts ADD CONSTRAINT FK_PortalWebPartPages FOREIGN KEY (PortalPageId) REFERENCES core.PortalPages (rowId);
GO
