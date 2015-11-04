
-- Provisioned schema used by DataClassDomainKind
CREATE SCHEMA expdataclass;

CREATE TABLE exp.DataClass
(
  RowId SERIAL NOT NULL,
  Name VARCHAR(200) NOT NULL,
  LSID LSIDtype NOT NULL,
  Container ENTITYID NOT NULL,
  Created TIMESTAMP NULL,
  CreatedBy INT NULL,
  Modified TIMESTAMP NULL,
  ModifiedBy INT NULL,
  Description TEXT NULL,
  MaterialSourceId INT NULL,
  NameExpression VARCHAR(200) NULL,

  CONSTRAINT PK_DataClass PRIMARY KEY (RowId),
  CONSTRAINT UQ_DataClass_LSID UNIQUE (LSID),
  CONSTRAINT UQ_DataClass_Container_Name UNIQUE (Container, Name),

  CONSTRAINT FK_DataClass_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
  CONSTRAINT FK_DataClass_MaterialSource FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);
CREATE INDEX IX_DataClass_Container ON exp.DataClass(Container);


ALTER TABLE exp.data
  ADD COLUMN description VARCHAR(4000);

ALTER TABLE exp.data
  ADD COLUMN classId INT;

ALTER TABLE exp.data
  ADD CONSTRAINT FK_Data_DataClass FOREIGN KEY (classId) REFERENCES exp.DataClass (rowid);

-- Within a DataClass, name must be unique.  If DataClass is null, duplicate names are allowed.
ALTER TABLE exp.data
  ADD CONSTRAINT UQ_Data_DataClass_Name UNIQUE (classId, name);

