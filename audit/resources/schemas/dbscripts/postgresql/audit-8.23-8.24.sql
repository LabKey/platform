/*
 * Copyright (c) 2008 LabKey Corporation
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
UPDATE audit.AuditLog SET IntKey1 = '0' WHERE IntKey1 IS NULL;
UPDATE audit.AuditLog SET IntKey2 = '0' WHERE IntKey2 IS NULL;
UPDATE audit.AuditLog SET IntKey3 = '0' WHERE IntKey3 IS NULL;
UPDATE audit.AuditLog SET CreatedBy = '0' WHERE CreatedBy IS NULL;

ALTER TABLE audit.AuditLog ALTER COLUMN IntKey1 SET NOT NULL;
ALTER TABLE audit.AuditLog ALTER COLUMN IntKey2 SET NOT NULL;
ALTER TABLE audit.AuditLog ALTER COLUMN IntKey3 SET NOT NULL;
ALTER TABLE audit.AuditLog ALTER COLUMN CreatedBy SET NOT NULL;
