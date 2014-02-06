/*
 * Copyright (c) 2014 LabKey Corporation
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

-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits DROP CONSTRAINT pk_studydesignunits;
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name NVARCHAR(5) NOT NULL;
ALTER TABLE study.StudyDesignUnits ADD CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name);