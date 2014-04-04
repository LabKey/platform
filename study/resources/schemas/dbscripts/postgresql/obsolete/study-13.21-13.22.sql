/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

-- Create an owner colunn to represent shared or private participant categories
ALTER TABLE study.ParticipantCategory ADD COLUMN OwnerId USERID NOT NULL DEFAULT -1;
UPDATE study.ParticipantCategory SET OwnerId = CreatedBy WHERE NOT shared;

ALTER TABLE study.ParticipantCategory DROP CONSTRAINT uq_label_container;
ALTER TABLE study.ParticipantCategory DROP COLUMN shared;
ALTER TABLE study.ParticipantCategory ADD CONSTRAINT uq_label_container_owner UNIQUE(Label, Container, OwnerId);


