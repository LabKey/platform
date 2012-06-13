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

-- Use prefix naming to better match new field names
ALTER TABLE exp.List RENAME COLUMN IndexMetaData TO MetaDataIndex;

ALTER TABLE exp.list ADD EntireListIndex BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exp.list ADD EntireListTitleSetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EntireListTitleTemplate VARCHAR(1000) NULL;
ALTER TABLE exp.list ADD EntireListBodySetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EntireListBodyTemplate VARCHAR(1000) NULL;

ALTER TABLE exp.list ADD EachItemIndex BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exp.list ADD EachItemTitleSetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EachItemTitleTemplate VARCHAR(1000) NULL;
ALTER TABLE exp.list ADD EachItemBodySetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EachItemBodyTemplate VARCHAR(1000) NULL;

