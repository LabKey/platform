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
/* core-12.30-12.31.sql */

ALTER TABLE core.ViewCategory
	ADD Parent INT;

GO

ALTER TABLE core.ViewCategory
	ADD CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (RowId) REFERENCES core.ViewCategory(RowId);

/* core-12.31-12.32.sql */

ALTER TABLE core.ViewCategory DROP CONSTRAINT uq_container_label;
ALTER TABLE core.ViewCategory ADD CONSTRAINT uq_container_label_parent UNIQUE (Container, Label, Parent);

/* core-12.32-12.33.sql */

ALTER TABLE core.PortalWebParts
  ADD Permission VARCHAR(256) NULL;

ALTER TABLE core.PortalWebParts
  ADD PermissionContainer ENTITYID NULL;

ALTER TABLE core.PortalWebParts
  ADD CONSTRAINT FK_PortalWebParts_PermissionContainer FOREIGN KEY (PermissionContainer) REFERENCES  core.Containers (EntityId);

/* core-12.33-12.34.sql */

ALTER TABLE core.PortalWebParts
  DROP CONSTRAINT FK_PortalWebParts_PermissionContainer;

ALTER TABLE core.PortalWebParts
  ADD CONSTRAINT FK_PortalWebParts_PermissionContainer FOREIGN KEY (PermissionContainer)
    REFERENCES  core.Containers (EntityId)
    ON UPDATE NO ACTION ON DELETE SET NULL;

/* core-12.34-12.35.sql */

-- clean up orphaned categories
DELETE FROM core.viewcategory WHERE parent IN
	(SELECT vcp.parent FROM (SELECT DISTINCT parent FROM core.viewcategory WHERE parent IS NOT NULL) vcp LEFT JOIN core.viewcategory vc ON vcp.parent = vc.rowid WHERE rowid IS NULL);

-- correct the fk constraint
ALTER TABLE core.viewcategory DROP CONSTRAINT FK_ViewCategory_Parent;
ALTER TABLE core.viewcategory ADD CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (parent) REFERENCES core.viewcategory(rowid);