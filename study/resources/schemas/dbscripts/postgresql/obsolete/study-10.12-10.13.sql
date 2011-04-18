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

-- Migrate from boolean key management type to None, RowId, and GUID
ALTER TABLE study.dataset ADD KeyManagementType VARCHAR(10);

UPDATE study.dataset SET KeyManagementType='RowId' WHERE KeyPropertyManaged = true;
UPDATE study.dataset SET KeyManagementType='None' WHERE KeyPropertyManaged = false;

ALTER TABLE study.dataset DROP COLUMN KeyPropertyManaged;
ALTER TABLE study.dataset ALTER KeyManagementType SET NOT NULL;
