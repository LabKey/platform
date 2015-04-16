/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

-- Change all the location booleans to NOT NULLABLE, #21616
UPDATE study.Site SET Repository = FALSE WHERE Repository IS NULL;
ALTER TABLE study.Site ALTER COLUMN Repository SET NOT NULL;
ALTER TABLE study.Site ALTER COLUMN Repository SET DEFAULT FALSE;

UPDATE study.Site SET Clinic = FALSE WHERE Clinic IS NULL;
ALTER TABLE study.Site ALTER COLUMN Clinic SET NOT NULL;
ALTER TABLE study.Site ALTER COLUMN Clinic SET DEFAULT FALSE;

UPDATE study.Site SET SAL = FALSE WHERE SAL IS NULL;
ALTER TABLE study.Site ALTER COLUMN SAL SET NOT NULL;
ALTER TABLE study.Site ALTER COLUMN SAL SET DEFAULT FALSE;

UPDATE study.Site SET EndPoint = FALSE WHERE EndPoint IS NULL;
ALTER TABLE study.Site ALTER COLUMN EndPoint SET NOT NULL;
ALTER TABLE study.Site ALTER COLUMN EndPoint SET DEFAULT FALSE;
