/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

/* study-11.30-11.31.sql */

ALTER TABLE study.Dataset ADD COLUMN Modified TIMESTAMP;

/* study-11.31-11.32.sql */

ALTER TABLE study.Dataset ADD COLUMN Type VARCHAR(50) NOT NULL DEFAULT 'Standard';

/* study-11.32-11.33.sql */

SELECT core.executeJavaUpgradeCode('migrateReportProperties');