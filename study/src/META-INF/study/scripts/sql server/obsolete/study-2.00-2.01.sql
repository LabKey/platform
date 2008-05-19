/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
-- VISITMAP
EXEC sp_rename 'study.visitmap.isrequired', 'required', 'COLUMN';
go


-- VISIT

-- ALTER TABLE study.visit ADD name NVARCHAR(200);
-- go
-- UPDATE study.visit SET name=COALESCE(label,cast(rowid as NVARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V where V.container=study.visit.container and V.name=study.visit.name)
-- go
-- 
-- ALTER TABLE study.visit ALTER COLUMN name VARCHAR(200) NOT NULL
-- go
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);
-- go


-- DATASET

ALTER TABLE study.dataset ADD name VARCHAR(200);
go
UPDATE study.dataset SET name=COALESCE(label,cast(datasetid as NVARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D where D.container=study.dataset.container and D.name=study.dataset.name)
go

ALTER TABLE study.dataset ALTER COLUMN name VARCHAR(200) NOT NULL
go
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
go