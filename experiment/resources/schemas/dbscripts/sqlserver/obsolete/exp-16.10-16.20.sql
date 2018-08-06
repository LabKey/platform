/*
 * Copyright (c) 2016 LabKey Corporation
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
/* exp-16.10-16.11.sql */

ALTER TABLE exp.data
   ALTER COLUMN cpastype nvarchar(300);

UPDATE exp.data
  SET cpastype = (SELECT lsid FROM exp.dataclass WHERE exp.data.classId = exp.dataclass.rowid)
  WHERE classid IS NOT NULL;

/* exp-16.11-16.12.sql */

ALTER TABLE exp.Data ADD LastIndexed DATETIME NULL;

/* exp-16.12-16.13.sql */

ALTER TABLE exp.DomainDescriptor ADD TemplateInfo NVARCHAR(4000) NULL;