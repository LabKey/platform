
CREATE TABLE core.Notifications
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,

  UserId USERID NOT NULL,
  ObjectId NVARCHAR(64) NOT NULL,
  Type NVARCHAR(200) NOT NULL,

  CONSTRAINT PK_Notifications PRIMARY KEY (RowId),
  CONSTRAINT FK_Notifications_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
  CONSTRAINT UQ_Notifications_ContainerUserObjectType UNIQUE (Container, UserId, ObjectId, Type)
);