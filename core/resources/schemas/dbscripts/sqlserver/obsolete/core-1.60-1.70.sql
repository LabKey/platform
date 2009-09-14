/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
ALTER TABLE core.UsersData ADD DisplayName NVARCHAR(64) NULL
GO

 DELETE FROM core.UsersData
 WHERE UserID NOT IN
 	(SELECT P1.UserId
  	FROM core.Principals P1
  	WHERE P1.Type = 'u')

UPDATE core.UsersData
SET core.UsersData.DisplayName =
	(SELECT Name
		FROM core.Principals P1
		WHERE P1.Type = 'u'
		AND P1.UserId = core.UsersData.UserId
	)
GO

ALTER TABLE core.UsersData ALTER COLUMN DisplayName NVARCHAR(64) NOT NULL
GO

ALTER TABLE core.UsersData ADD CONSTRAINT UQ_DisplayName UNIQUE (DisplayName)
GO

CREATE TABLE core.Report
(
    RowId INT IDENTITY(1,1) NOT NULL,
    ReportKey NVARCHAR(255),
    CreatedBy USERID,
    ModifiedBy USERID,
    Created DATETIME,
    Modified DATETIME,
    ContainerId ENTITYID NOT NULL,
    EntityId ENTITYID NULL,
    DescriptorXML TEXT,

    CONSTRAINT PK_Report PRIMARY KEY (RowId)
);
GO

