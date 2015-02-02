/*
 * Copyright (c) 2015 LabKey Corporation
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


CREATE SEQUENCE exp.domaindescriptor_ts;
ALTER TABLE exp.DomainDescriptor ADD _ts BIGINT DEFAULT nextval('exp.domaindescriptor_ts') NOT NULL;
ALTER TABLE exp.DomainDescriptor ADD COLUMN ModifiedBy USERID;
ALTER TABLE exp.DomainDescriptor ADD COLUMN Modified TIMESTAMP DEFAULT now();

