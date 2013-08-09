/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

-- Switch to nullable... random alternate IDs are now set via code
ALTER TABLE study.Participant ALTER COLUMN AlternateId DROP NOT NULL;
ALTER TABLE study.Participant ALTER COLUMN AlternateId DROP DEFAULT;
UPDATE study.Participant SET AlternateId = NULL;

-- Add columns to store an alternate ID "template", i.e., an optional prefix and number of digits to use when generating random alternate IDs
ALTER TABLE study.Study ADD AlternateIdPrefix VARCHAR(20) NULL;
ALTER TABLE study.Study ADD AlternateIdDigits INT NOT NULL DEFAULT 6;
