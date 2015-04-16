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

ALTER TABLE core.viewcategory
    ADD COLUMN Parent int4,
    ADD CONSTRAINT fk_viewcategory_parent FOREIGN KEY (rowid) REFERENCES core.viewcategory(rowid) ON DELETE CASCADE;

/* core-12.31-12.32.sql */

ALTER TABLE core.ViewCategory DROP CONSTRAINT uq_container_label;
ALTER TABLE core.ViewCategory ADD CONSTRAINT uq_container_label_parent UNIQUE (Container, Label, Parent);

/* core-12.32-12.33.sql */

ALTER TABLE core.portalwebparts
  ADD COLUMN permission character varying(256),
  ADD COLUMN permissioncontainer entityid,
  ADD CONSTRAINT fk_portalwebparts_permissioncontainer FOREIGN KEY (permissioncontainer)
      REFERENCES core.containers (entityid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION;

/* core-12.33-12.34.sql */

ALTER TABLE core.portalwebparts
  DROP CONSTRAINT fk_portalwebparts_permissioncontainer;

ALTER TABLE core.portalwebparts
  ADD CONSTRAINT fk_portalwebparts_permissioncontainer FOREIGN KEY (permissioncontainer)
    REFERENCES core.containers (entityid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE SET NULL;

/* core-12.34-12.35.sql */

-- clean up orphaned categories
DELETE FROM core.viewcategory WHERE parent IN
	(SELECT vcp.parent FROM (SELECT DISTINCT parent FROM core.viewcategory WHERE parent IS NOT NULL) vcp LEFT JOIN core.viewcategory vc ON vcp.parent = vc.rowid WHERE rowid IS NULL);

-- correct the fk constraint
ALTER TABLE core.viewcategory DROP CONSTRAINT fk_viewcategory_parent;
ALTER TABLE core.viewcategory ADD CONSTRAINT fk_viewcategory_parent FOREIGN KEY (parent) REFERENCES core.viewcategory(rowid);