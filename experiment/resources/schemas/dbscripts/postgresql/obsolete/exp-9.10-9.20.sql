/*
 * Copyright (c) 2009 LabKey Corporation
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

/* exp-9.10-9.11.sql */

ALTER TABLE exp.Data DROP COLUMN SourceProtocolLSID;

ALTER TABLE exp.Material DROP COLUMN SourceProtocolLSID;

/* exp-9.11-9.12.sql */

ALTER TABLE exp.ObjectProperty
  RENAME COLUMN QcValue TO MvIndicator;

ALTER TABLE exp.PropertyDescriptor ADD COLUMN MvEnabled BOOLEAN NOT NULL DEFAULT '0';

UPDATE exp.PropertyDescriptor SET MvEnabled = QcEnabled;

ALTER TABLE exp.PropertyDescriptor DROP COLUMN QcEnabled;