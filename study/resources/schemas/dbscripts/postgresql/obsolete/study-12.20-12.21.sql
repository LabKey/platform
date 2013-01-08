/*
 * Copyright (c) 2012 LabKey Corporation
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

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD COLUMN DateOffset INT DEFAULT CAST((RANDOM() * 364 + 1) AS INT);

-- Default to random 8-digit ID
ALTER TABLE study.Participant ADD COLUMN AlternateId VARCHAR(32) DEFAULT CAST((RANDOM() * 90000000 + 10000000) AS INT);

