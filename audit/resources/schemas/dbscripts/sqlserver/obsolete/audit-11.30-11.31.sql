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

ALTER TABLE audit.auditlog ALTER COLUMN intkey1 int NULL;
ALTER TABLE audit.auditlog ALTER COLUMN intkey2 int NULL;
ALTER TABLE audit.auditlog ALTER COLUMN intkey3 int NULL;

UPDATE audit.auditlog SET intkey1=NULL WHERE intkey1=0 AND eventtype='GroupAuditEvent';
UPDATE audit.auditlog SET intkey2=NULL WHERE intkey2=0 AND eventtype='GroupAuditEvent';
UPDATE audit.auditlog SET intkey3=NULL WHERE intkey3=0 AND eventtype='GroupAuditEvent';
