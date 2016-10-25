/* study-16.20-16.21.sql */

EXEC core.executeJavaUpgradeCode 'updatePKAndContainer';

/* study-16.21-16.22.sql */

CREATE TABLE study.DoseAndRoute
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Label NVARCHAR(600),
  Dose NVARCHAR(200),
  Route NVARCHAR(200),
  ProductId INT NOT NULL,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_DoseAndRoute PRIMARY KEY (RowId),
  CONSTRAINT DoseAndRoute_Dose_Route_ProductId UNIQUE (Dose, Route, ProductId)
);

EXEC core.executeJavaUpgradeCode 'populateDoseAndRoute';

/* study-16.22-16.23.sql */

ALTER TABLE study.DoseAndRoute DROP CONSTRAINT DoseAndRoute_Dose_Route_ProductId;
ALTER TABLE study.DoseAndRoute ADD CONSTRAINT DoseAndRoute_Container_Dose_Route_ProductId UNIQUE (Container, Dose, Route, ProductId);

/* study-16.23-16.24.sql */

EXEC core.executeJavaUpgradeCode 'updateDateIndex';

/* study-16.24-16.25.sql */

ALTER TABLE study.AssaySpecimen ADD SampleQuantity DOUBLE PRECISION;
ALTER TABLE study.AssaySpecimen ADD SampleUnits NVARCHAR(5);

/* study-16.25-16.26.sql */

ALTER TABLE study.DoseAndRoute DROP COLUMN Label;

/* study-16.26-16.27.sql */

CREATE TABLE study.StudyDesignChallengeTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignchallengetypes PRIMARY KEY (Container, Name)
);