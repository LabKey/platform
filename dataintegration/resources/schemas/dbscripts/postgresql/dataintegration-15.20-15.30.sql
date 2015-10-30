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
/* dataintegration-15.20-15.21.sql */

ALTER TABLE dataintegration.TransformConfiguration DROP CONSTRAINT UQ_TransformConfiguration_TransformId;
ALTER TABLE dataintegration.TransformConfiguration ALTER COLUMN TransformId TYPE varchar(100);
ALTER TABLE dataintegration.TransformConfiguration ADD CONSTRAINT UQ_TransformConfiguration_TransformId UNIQUE (Container, TransformId);

/* dataintegration-15.21-15.22.sql */

ALTER TABLE dataintegration.TransformRun DROP CONSTRAINT FK_TransformRun_ExpRunId;
ALTER TABLE dataintegration.TransformRun DROP COLUMN ExpRunId;