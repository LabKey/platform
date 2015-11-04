
-- Provisioned schema used by DataClassDomainKind
CREATE SCHEMA expdataclass;
GO

CREATE TABLE exp.DataClass
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Name NVARCHAR(200) NOT NULL,
  LSID LSIDtype NOT NULL,
  Container EntityId NOT NULL,
  Created DATETIME NULL,
  CreatedBy INT NULL,
  Modified DATETIME NULL,
  ModifiedBy INT NULL,
  Description NTEXT NULL,
  MaterialSourceId INT NULL,
  NameExpression NVARCHAR(200) NULL,

  CONSTRAINT PK_DataClass PRIMARY KEY (RowId),
  CONSTRAINT UQ_DataClass_LSID UNIQUE (LSID),
  CONSTRAINT UQ_DataClass_Container_Name UNIQUE (Container, Name),

  CONSTRAINT FK_DataClass_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
  CONSTRAINT FK_DataClass_MaterialSource FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);
CREATE INDEX IX_DataClass_Container ON exp.DataClass(Container);


ALTER TABLE exp.data
  ADD description NVARCHAR(4000);

GO

ALTER TABLE exp.data
  ADD classId INT;

GO

ALTER TABLE exp.data
  ADD CONSTRAINT FK_Data_DataClass FOREIGN KEY (classId) REFERENCES exp.DataClass (rowid);

-- Within a DataClass, name must be unique.  If DataClass is null, duplicate names are allowed.
CREATE UNIQUE INDEX UQ_Data_DataClass_Name
  ON exp.data(classId, name) WHERE classId IS NOT NULL;


