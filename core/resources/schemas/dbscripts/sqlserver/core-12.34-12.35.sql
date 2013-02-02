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

-- clean up orphaned categories
DELETE FROM core.viewcategory WHERE parent IN
	(SELECT vcp.parent FROM (SELECT DISTINCT parent FROM core.viewcategory WHERE parent IS NOT NULL) vcp LEFT JOIN core.viewcategory vc ON vcp.parent = vc.rowid WHERE rowid IS NULL);

-- correct the fk constraint
ALTER TABLE core.viewcategory DROP CONSTRAINT FK_ViewCategory_Parent;
ALTER TABLE core.viewcategory ADD CONSTRAINT FK_ViewCategory_Parent FOREIGN KEY (parent) REFERENCES core.viewcategory(rowid);

