CREATE SCHEMA comm;

CREATE TABLE comm.Announcements
	(
	RowId SERIAL,
	EntityId ENTITYID NOT NULL,
	CreatedBy USERID,
	Created TIMESTAMP,
	ModifiedBy USERID,
	Modified TIMESTAMP,
	Owner USERID,
	Container ENTITYID NOT NULL,
	Parent ENTITYID,
	Title VARCHAR(255),
	Expires TIMESTAMP,
	Body TEXT,

	CONSTRAINT PK_Announcements PRIMARY KEY (RowId),
	CONSTRAINT UQ_Announcements UNIQUE (Container, Parent, RowId)
	);


