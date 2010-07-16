/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

ALTER TABLE study.UploadLog DROP CONSTRAINT UQ_UploadLog_FilePath;

-- This PostgreSQL script corresponds to a more in-depth set of changes on the SQL Server side where a
-- number of VARCHAR columns were converted to NVARCHAR to accommodate wide characters.  As part of that
-- change, we needed to shorten this column from 512 to 450 characters to stay under
-- SQL Server's maximum key length of 900 bytes.  The change is made on PostgreSQL as well for consistency:
ALTER TABLE study.UploadLog ALTER COLUMN FilePath TYPE VARCHAR(400);

ALTER TABLE study.UploadLog ADD
	CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath);