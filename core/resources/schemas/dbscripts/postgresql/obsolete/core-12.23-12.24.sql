/*
 * Copyright (c) 2012 LabKey Corporation
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


-- CONSIDER: eventually switch to entityid PK/FK

CREATE TABLE portal.pages
(
    EntityId ENTITYID NULL,
    Container ENTITYID NOT NULL,
    PageId VARCHAR(50) NOT NULL,
    Index INTEGER NOT NULL DEFAULT 0,
    Caption VARCHAR(64),
    Hidden BOOLEAN NOT NULL DEFAULT false,
    Type VARCHAR(20), -- 'portal', 'folder', 'action'
    -- associate page with a registered folder type
    -- folderType varchar(64),
    Action VARCHAR(200),    -- type='action' see DetailsURL
    TargetFolder ENTITYID,  -- type=='folder'
    Permanent BOOLEAN NOT NULL DEFAULT false, -- may not be renamed,hidden,deleted (w/o changing folder type)
    Properties TEXT,

    CONSTRAINT PK_PortalPages PRIMARY KEY (container, pageid),
    CONSTRAINT FK_PortalPages_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);


-- find all explicit tabs configured by folder types
INSERT INTO portal.pages (container,pageid,index,type)
SELECT
  container,
  name as pageid,
  index,
  CASE WHEN pageid='Manage' THEN 'action' ELSE 'portal' END as type
FROM portal.portalwebparts
WHERE location = 'tab';

DELETE FROM portal.portalwebparts
WHERE location = 'tab';


-- default portal pages
INSERT INTO portal.pages (container,pageid,index,type)
SELECT DISTINCT
  container,
  pageid,
  0 as index,
  'portal' as type
FROM portal.portalwebparts PWP
WHERE location != 'tab' AND
  NOT EXISTS (SELECT * from portal.pages PP where PP.container=PWP.container AND PP.pageid = PWP.pageid);


-- containers with missing pages
INSERT INTO portal.pages (container, pageid, index)
SELECT C.entityid, 'portal.default', 0
FROM core.containers C
WHERE C.entityid not in (select container from portal.pages);


-- FK
ALTER TABLE Portal.PortalWebParts
    ADD CONSTRAINT FK_PortalWebPartPages FOREIGN KEY (container,pageid) REFERENCES portal.pages (container,pageid);

CLUSTER PK_PortalPages ON portal.pages;
CLUSTER ix_portalwebparts ON portal.portalwebparts;
