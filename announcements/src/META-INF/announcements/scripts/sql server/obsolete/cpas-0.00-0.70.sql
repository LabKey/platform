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

	CONSTRAINT PK_Announcements PRIMARY KEY (RowId),
	CONSTRAINT UQ_Announcements UNIQUE CLUSTERED (Container, Parent, RowId)
	)
GO
