/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

SET QUOTED_IDENTIFIER ON

EXEC sp_addtype 'ENTITYID', 'UNIQUEIDENTIFIER'
EXEC sp_addtype 'USERID', 'INT'
GO


CREATE VIEW Documents AS
	SELECT *
	FROM core..Documents
GO


--
-- NOTE: Bug in PropertyManager means NULL can't be passed for ObjectId, Category, etc. right now
--
-- ObjectId: EntityId or ContainerId this setting applies to
--     Generally only global User settings would use NULL ObjectId
-- Category: Category can be used to group related properties,
--     Category can be NULL if the Key field is reasonably unique/descriptive
-- UserId: Modules may use NULL UserId for general configuration
--
CREATE TABLE PropertySets
    (
    "Set" INT IDENTITY(1,1),
    ObjectId UNIQUEIDENTIFIER NULL,  -- e.g. EntityId or ContainerID
    Category VARCHAR(255) NULL,      -- e.g. "org.fhcrc.cpas.MailingList", may be NULL
    UserId USERID,

    CONSTRAINT PK_PropertySet PRIMARY KEY CLUSTERED ("Set"),
    CONSTRAINT UQ_PropertySet UNIQUE (ObjectId, UserId, Category)
    )


CREATE TABLE Properties
    (
    "Set" INT NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Value VARCHAR(2000) NOT NULL,

    CONSTRAINT PK_Properties PRIMARY KEY CLUSTERED ("Set", Name)
    )
GO


CREATE VIEW PropertyEntries AS
    SELECT ObjectId, Category, UserId, Name, Value FROM Properties JOIN PropertySets ON PropertySets."Set" = Properties."Set"
GO


CREATE PROCEDURE Property_setValue(@Set int, @Name VARCHAR(255), @Value VARCHAR(2000)) AS
    BEGIN
    IF (@Value IS NULL)
        DELETE Properties WHERE "Set" = @Set AND Name = @Name
    ELSE
        BEGIN
        UPDATE Properties SET Value = @Value WHERE "Set" = @Set AND Name = @Name
        IF (@@ROWCOUNT = 0)
            INSERT Properties VALUES (@Set, @Name, @Value)
        END
    END
GO


CREATE TABLE TestTable
	(
	_ts TIMESTAMP,
	EntityId ENTITYID DEFAULT NEWID(),
	RowId INT IDENTITY(1,1),
	CreatedBy USERID,
	Created DATETIME,

	Container ENTITYID,			--container/path
	Text NVARCHAR(195),		--filename
	
	IntNull INT NULL,
	IntNotNull INT NOT NULL,
	DatetimeNull DATETIME NULL,
	DatetimeNotNull DATETIME NOT NULL,
	RealNull REAL NULL,
	BitNull Bit NULL,
	BitNotNull Bit NOT NULL,

	CONSTRAINT PK_TestTable PRIMARY KEY (RowId)
	)
GO


-- Used in the Contacts webpart to list contact info for everyone in a particular group + container
CREATE VIEW Contacts As
	SELECT Users.FirstName + '&nbsp;' + Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, core..Principals.ProjectId, core..Principals.Name AS GroupName
	FROM core..Principals INNER JOIN
	core..Members ON core..Principals.UserId = core..Members.GroupId INNER JOIN
        Users ON core..Members.UserId = Users.UserId
GO


CREATE TABLE PortalWebParts
	(
	PageId ENTITYID NOT NULL,
	[Index] INT NOT NULL,
	Name VARCHAR(64),
	Location VARCHAR(16),	-- 'body', 'left', 'right'

	Properties VARCHAR(4000),	-- url encoded properties

	CONSTRAINT PK_PortalWebParts PRIMARY KEY (PageId, [Index])
	)
GO