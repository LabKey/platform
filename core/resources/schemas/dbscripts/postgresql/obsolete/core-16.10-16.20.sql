/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

/* core-16.10-16.11.sql */

ALTER TABLE core.Notifications ADD Content TEXT;
ALTER TABLE core.Notifications ADD ContentType VARCHAR(100);

UPDATE core.Notifications SET Content = Description, ContentType='text/plain';
ALTER TABLE core.Notifications DROP COLUMN Description;

/* core-16.11-16.12.sql */

ALTER TABLE core.Logins ADD RequestedEmail VARCHAR(255);
ALTER TABLE core.Logins ADD VerificationTimeout TIMESTAMP;