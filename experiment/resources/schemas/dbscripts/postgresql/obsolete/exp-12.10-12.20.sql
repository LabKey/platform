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

/* exp-12.10-12.11.sql */

ALTER TABLE exp.PropertyDescriptor ADD COLUMN FacetingBehaviorType VARCHAR(40) NOT NULL DEFAULT 'AUTOMATIC';

/* exp-12.11-12.12.sql */

ALTER TABLE exp.IndexVarchar ADD COLUMN CreatedBy USERID NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN Created TIMESTAMP NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN ModifiedBy USERID NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN Modified TIMESTAMP NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN LastIndexed TIMESTAMP NULL;

ALTER TABLE exp.IndexInteger ADD COLUMN CreatedBy USERID NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN Created TIMESTAMP NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN ModifiedBy USERID NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN Modified TIMESTAMP NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN LastIndexed TIMESTAMP NULL;

/* exp-12.12-12.13.sql */

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

/* exp-12.13-12.14.sql */

ALTER TABLE exp.List ADD LastIndexed TIMESTAMP NULL;

/* exp-12.14-12.15.sql */

-- Merge the "meta data only" and "entire list data" settings, migrating them to a single boolean (EntireListIndex) plus
-- a setting denoting what to index (EntireListIndexSetting = meta data only (0), item data only (1), or both (2))

ALTER TABLE exp.List ADD EntireListIndexSetting INT NOT NULL DEFAULT 0;  -- Meta data only, the default

UPDATE exp.List SET EntireListIndexSetting = 1 WHERE MetaDataIndex = FALSE AND EntireListIndex = TRUE; -- Item data only
UPDATE exp.List SET EntireListIndexSetting = 2 WHERE MetaDataIndex = TRUE AND EntireListIndex = TRUE;  -- Meta data and item data

UPDATE exp.List SET EntireListIndex = TRUE WHERE MetaDataIndex = TRUE;   -- Turn on EntireListIndex if meta data was being indexed

ALTER TABLE exp.List DROP MetaDataIndex;

/* exp-12.15-12.16.sql */

ALTER TABLE exp.ExperimentRun ADD JobId INTEGER;

--experiment module depends on pipeline, so this should be ok
ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_JobId FOREIGN KEY (JobId)
        REFERENCES pipeline.statusfiles (RowId);

/* exp-12.16-12.17.sql */

-- Change exp.MaterialSource.Name from VARCHAR(50) to VARCHAR(100). Going to 200 to match other experiment tables
 -- hits limits with domain URIs, etc
ALTER TABLE exp.MaterialSource ALTER COLUMN Name TYPE VARCHAR(100);

-- Change exp.ExperimentRun.Name from VARCHAR(100) to VARCHAR(200) to match other experiment table name columns
ALTER TABLE exp.ExperimentRun ALTER COLUMN Name TYPE VARCHAR(200);

-- Rename any list field named Created, CreatedBy, Modified, or ModifiedBy since these are now built-in columns on every list
UPDATE exp.PropertyDescriptor SET Name = Name || '_99' WHERE LOWER(Name) IN ('created', 'createdby', 'modified', 'modifiedby') AND
    PropertyId IN (SELECT PropertyId FROM exp.PropertyDomain pdom INNER JOIN exp.List l ON pdom.DomainId = l.DomainId);

