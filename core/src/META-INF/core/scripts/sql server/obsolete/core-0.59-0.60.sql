-- Table for all modules
CREATE TABLE Modules
    (
    Name NVARCHAR(255),
    ClassName NVARCHAR(255),
    InstalledVersion FLOAT,
    Enabled BIT DEFAULT 1,

    CONSTRAINT Modules_PK PRIMARY KEY (Name)
    )
GO


-- Table to keep track of SQL scripts that have been run on a given installation
CREATE TABLE SqlScripts
	(
	-- standard fields
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

	ModuleName NVARCHAR(100),
	FileName NVARCHAR(300),

	CONSTRAINT SqlScripts_PK PRIMARY KEY (ModuleName, FileName)
	)
GO

