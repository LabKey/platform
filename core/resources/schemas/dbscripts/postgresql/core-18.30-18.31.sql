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

SELECT core.fn_dropifexists('portalwebparts', 'core', 'CONSTRAINT', 'fk_portalwebpartpages');
SELECT core.fn_dropifexists('portalpages', 'core', 'CONSTRAINT', 'pk_portalpages');
SELECT core.fn_dropifexists('portalpages', 'core', 'CONSTRAINT', 'uq_portalpage');
ALTER TABLE core.portalpages ADD COLUMN rowId SERIAL PRIMARY KEY;
ALTER TABLE core.portalwebparts ADD COLUMN portalPageId INTEGER;
UPDATE core.portalwebparts web SET portalPageId = page.rowId
    FROM core.portalpages page
    WHERE web.pageId = page.pageId;
ALTER TABLE core.portalwebparts DROP COLUMN pageId;
ALTER TABLE core.portalwebparts ALTER COLUMN portalPageId SET NOT NULL;
ALTER TABLE core.portalwebparts ADD CONSTRAINT fk_portalwebpartpages FOREIGN KEY (portalPageId) REFERENCES core.portalpages (rowId);
