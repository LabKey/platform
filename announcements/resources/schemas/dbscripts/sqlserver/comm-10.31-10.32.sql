/*
 * Copyright (c) 2011 LabKey Corporation
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

-- add a new column to contain 'notification type' information for both the
-- email prefs and options
ALTER TABLE comm.EmailOptions ADD Type NVARCHAR(60) NOT NULL DEFAULT 'messages'
GO
ALTER TABLE comm.EmailPrefs ADD Type NVARCHAR(60) NOT NULL DEFAULT 'messages'
GO

ALTER TABLE comm.EmailPrefs DROP CONSTRAINT PK_EmailPrefs
GO
ALTER TABLE comm.EmailPrefs ADD CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type)
GO

-- new file email notification options
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES (512, 'No Email', 'files')
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES (513, '15 minute digest', 'files')
INSERT INTO comm.emailOptions (EmailOptionId, EmailOption, Type) VALUES (514, 'Daily digest', 'files')
GO

-- migrate existing file setting from property manager props
INSERT INTO comm.emailPrefs (Container, UserId, EmailOptionId, EmailFormatId, PageTypeId, Type) SELECT
	ObjectId,
	UserId,
	CAST(Value AS INT) + 512,
	1, 0, 'files'
	FROM prop.Properties props JOIN prop.PropertySets ps on props."set" = ps."set" AND category = 'EmailService.emailPrefs' WHERE name = 'FileContentEmailPref' AND value <> '-1'
GO

-- update folder default settings
UPDATE prop.Properties SET value = '512' WHERE name = 'FileContentDefaultEmailPref' AND value = '0'
GO
UPDATE prop.Properties SET value = '513' WHERE name = 'FileContentDefaultEmailPref' AND value = '1'
GO

-- delete old user property values
DELETE FROM prop.Properties WHERE name = 'FileContentEmailPref'
GO