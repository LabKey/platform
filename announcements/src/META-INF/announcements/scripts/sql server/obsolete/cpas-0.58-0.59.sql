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
