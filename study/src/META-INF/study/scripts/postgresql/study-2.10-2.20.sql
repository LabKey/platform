/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
ALTER TABLE study.SampleRequest ADD
    EntityId ENTITYID NULL;

ALTER TABLE study.SampleRequestRequirement
    ADD OwnerEntityId ENTITYID NULL,
    DROP CONSTRAINT FK_SampleRequestRequirement_SampleRequest;

CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);

DROP TABLE study.AssayRun;

ALTER TABLE study.Specimen
    ADD Requestable Boolean;

ALTER TABLE study.SpecimenEvent
    ADD freezer VARCHAR(200),
    ADD fr_level1 VARCHAR(200),
    ADD fr_level2 VARCHAR(200),
    ADD fr_container VARCHAR(200),
    ADD fr_position VARCHAR(200);

DELETE FROM study.study WHERE NOT EXISTS
(SELECT Container FROM study.visit WHERE study.visit.container=study.study.Container)
AND NOT EXISTS (Select Container FROM study.dataset WHERE study.dataset.container=study.study.Container);
