/*
 * Copyright (c) 2011 LabKey Corporation
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

/* portal-11.20-11.24.sql */

-- Add an index and FK on the Container column
CREATE INDEX IX_PortalWebParts ON portal.PortalWebParts(Container);

DELETE FROM portal.PortalWebParts WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE portal.PortalWebParts
    ADD CONSTRAINT FK_PortalWebParts_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

ALTER TABLE portal.PortalWebParts ALTER COLUMN PageId VARCHAR(50);

UPDATE portal.PortalWebParts SET PageId = 'portal.default' WHERE PageId = CAST(Container AS VARCHAR(50));

/* portal-11.26-11.27.sql */

UPDATE portal.PortalWebParts SET PageId = 'study.PARTICIPANTS' WHERE PageId = 'study.SHORTCUTS';
UPDATE portal.PortalWebParts SET Name = 'study.PARTICIPANTS' WHERE Name = 'study.SHORTCUTS' AND PageId = 'folderTab';
