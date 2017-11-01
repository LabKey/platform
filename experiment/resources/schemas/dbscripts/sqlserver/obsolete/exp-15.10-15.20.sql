/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

/* exp-15.10-15.11.sql */

EXECUTE core.fn_dropifexists 'material', 'exp', 'INDEX', 'idx_material_AK';

-- shouldn't any null names, but just to be safe
UPDATE exp.material SET name='NULL' WHERE name IS NULL;
ALTER TABLE exp.material ALTER COLUMN name NVARCHAR(200) NOT NULL;

WITH nonunique AS (
    SELECT container, cpastype, name
    FROM exp.material
      WHERE cpastype IS NOT NULL
    GROUP BY container, cpastype, name
    HAVING COUNT(*) > 1)
UPDATE exp.material
SET Name = Name + ' - ' + CAST(RowId AS NVARCHAR(11))
FROM exp.material M
WHERE EXISTS (SELECT * FROM nonunique NU WHERE M.container=NU.container AND M.cpastype=NU.cpastype AND M.name=NU.name);

CREATE UNIQUE INDEX idx_material_AK ON exp.material (container, cpastype, name) WHERE cpastype IS NOT NULL;
GO

/* exp-15.11-15.12.sql */

EXEC sp_rename 'exp.PropertyDescriptor.KeyVariable', 'RecommendedVariable', 'COLUMN';

/* exp-15.12.15.13.sql */

ALTER TABLE exp.Data ADD Generated BIT NOT NULL DEFAULT 0;