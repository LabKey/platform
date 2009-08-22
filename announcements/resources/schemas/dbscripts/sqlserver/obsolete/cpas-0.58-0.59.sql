/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
DROP VIEW Announcements
GO


CREATE TABLE Announcements
	(
	RowId INT IDENTITY(1,1) NOT NULL,
	EntityId ENTITYID NOT NULL,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID,
	Container ENTITYID NOT NULL,
	Parent ENTITYID,
	Title NVARCHAR(255),
	Expires DATETIME,
	Body NTEXT,

	CONSTRAINT Announcements_PK PRIMARY KEY (RowId),
	CONSTRAINT Announcements_AK UNIQUE CLUSTERED (Container, Parent, RowId)
	)
GO


SET IDENTITY_INSERT Announcements ON
INSERT INTO Announcements (RowId, EntityId, CreatedBy, Created, ModifiedBy, Modified, Owner, Container, Parent, Title, Expires, Body)
SELECT RowId, EntityId, CreatedBy, Created, ModifiedBy, Modified, Owner, Container, Parent, Title, Datetime1, Body
FROM core..Lists
WHERE EntityType = (SELECT EntityType FROM core..EntityTypes WHERE Name = 'Announcement')
SET IDENTITY_INSERT Announcements OFF
GO
