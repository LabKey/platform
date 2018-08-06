/*
 * Copyright (c) 2016 LabKey Corporation
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

ALTER TABLE core.Notifications ADD Content NVARCHAR(MAX);
ALTER TABLE core.Notifications ADD ContentType NVARCHAR(100);
GO

UPDATE core.Notifications SET Content = Description, ContentType='text/plain';
ALTER TABLE core.Notifications DROP COLUMN Description;
GO

/* core-16.11-16.12.sql */

ALTER TABLE core.Logins ADD RequestedEmail NVARCHAR(255);
ALTER TABLE core.Logins ADD VerificationTimeout DATETIME;