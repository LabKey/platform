/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

/* core-13.20-13.21.sql */

-- Support longer lists of module schemas (which now may include data source prefixes)
ALTER TABLE core.Modules
    ALTER COLUMN Schemas NVARCHAR(4000) NULL;

/* core-13.22-13.23.sql */

-- We no longer call this inline. Instead, we ensure GROUP_CONCAT in CoreModule.afterUpdate(), see #18979

-- Install/upgrade new version (1.00.23696) of GROUP_CONCAT aggregate function on SQL Server
-- This version fixes concurrency and performance issues, see #18600
-- EXEC core.executeJavaUpgradeCode 'installGroupConcat';