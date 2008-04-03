CREATE SCHEMA biotrue;

CREATE TABLE biotrue.server(
RowId SERIAL NOT NULL,
Name VARCHAR(256) NOT NULL,
Container ENTITYID NOT NULL,
WsdlURL VARCHAR(1024) NULL,
ServiceNamespaceURI VARCHAR(256) NULL,
ServiceLocalPart VARCHAR(256) NULL,
UserName VARCHAR(256) NULL,
Password VARCHAR(256) NULL,
PhysicalRoot VARCHAR(500) NULL,
CONSTRAINT PK_Server PRIMARY KEY(RowId),
CONSTRAINT UQ_Server UNIQUE(Container, Name)
);

CREATE TABLE biotrue.entity(
RowId SERIAL NOT NULL,
ServerId INT NOT NULL,
ParentId INT NOT NULL,
BioTrue_Id VARCHAR(256) NOT NULL,
BioTrue_Type VARCHAR(256) NULL,
BioTrue_Name VARCHAR(256) NOT NULL,
PhysicalName VARCHAR(256),
CONSTRAINT PK_Entity PRIMARY KEY(RowId),
CONSTRAINT FK_Entity_Server FOREIGN KEY(ServerId) REFERENCES biotrue.Server(RowId),
CONSTRAINT UQ_Entity_BioTrue_Parent_Id UNIQUE(ServerId, ParentId, BioTrue_Id)
);

CREATE TABLE biotrue.task(
RowId SERIAL NOT NULL,
ServerId INT NOT NULL,
EntityId INT NOT NULL,
Operation VARCHAR(50) NOT NULL,
Started TIMESTAMP NULL,
CONSTRAINT PK_Task PRIMARY KEY(RowId)
);

CREATE INDEX IX_Task_Server ON biotrue.task(ServerId, RowId);
CREATE INDEX IX_Task_Entity ON biotrue.task(EntityId);