/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

-- Some versions of SQL Server require NOT NULL for the DEFAULT to actually work, so drop everything and try again
EXEC core.fn_dropifexists 'Participant', 'study', 'DEFAULT', 'DateOffset';
ALTER TABLE study.Participant DROP COLUMN DateOffset;
EXEC core.fn_dropifexists 'Participant', 'study', 'DEFAULT', 'AlternateId';
ALTER TABLE study.Participant DROP COLUMN AlternateId;

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD DateOffset INT NOT NULL DEFAULT ABS(CHECKSUM(NEWID())) % 364 + 1;

-- Default to random 8-digit ID
ALTER TABLE study.Participant ADD AlternateId VARCHAR(32) NOT NULL DEFAULT ABS(CHECKSUM(NEWID())) % 90000000 + 10000000;