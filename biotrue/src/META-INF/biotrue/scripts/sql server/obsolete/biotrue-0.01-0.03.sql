EXEC sp_addapprole 'biotrue', 'password'
GO

CREATE TABLE biotrue.server
(
    RowId INT IDENTITY(1,1),
    Name NVARCHAR(256) NOT NULL,
    Container ENTITYID NOT NULL,
    WsdlURL NVARCHAR(1024) NULL,
    ServiceNamespaceURI NVARCHAR(256) NULL,
    ServiceLocalPart NVARCHAR(256) NULL,
    UserName NVARCHAR(256) NULL,
    Password NVARCHAR(256) NULL,
    PhysicalRoot NVARCHAR(500) NULL,
    SyncInterval INT NOT NULL DEFAULT 0,
    NextSync DATETIME,

    CONSTRAINT PK_Server PRIMARY KEY(RowId),
    CONSTRAINT UQ_Server UNIQUE(Container, Name)
);
GO

CREATE TABLE biotrue.entity
(
    RowId INT IDENTITY(1,1),
    ServerId INT NOT NULL,
    ParentId INT NOT NULL,
    BioTrue_Id NVARCHAR(256) NOT NULL,
    BioTrue_Type NVARCHAR(256) NULL,
    BioTrue_Name NVARCHAR(256) NOT NULL,
    PhysicalName NVARCHAR(256),
    CONSTRAINT PK_Entity PRIMARY KEY(RowId),
    CONSTRAINT FK_Entity_Server FOREIGN KEY(ServerId) REFERENCES biotrue.Server(RowId),
    CONSTRAINT UQ_Entity_BioTrue_Parent_Id UNIQUE(ServerId, ParentId, BioTrue_Id)
);
GO

CREATE TABLE biotrue.task
(
    RowId INT IDENTITY(1,1),
    ServerId INT NOT NULL,
    EntityId INT NOT NULL,
    Operation NVARCHAR(50) NOT NULL,
    Started DATETIME NULL,
    CONSTRAINT PK_Task PRIMARY KEY(RowId)
);
GO

CREATE INDEX IX_Task_Server ON biotrue.task(ServerId, RowId);
CREATE INDEX IX_Task_Entity ON biotrue.task(EntityId);
GO
