/* study-16.20-16.21.sql */

SELECT core.executeJavaUpgradeCode('updatePKAndContainer');

/* study-16.21-16.22.sql */

CREATE TABLE study.DoseAndRoute
(
  RowId SERIAL,
  Label VARCHAR(600),
  Dose VARCHAR(200),
  Route VARCHAR(200),
  ProductId INT NOT NULL,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_DoseAndRoute PRIMARY KEY (RowId),
  CONSTRAINT DoseAndRoute_Dose_Route_ProductId UNIQUE (Dose, Route, ProductId)
);

SELECT core.executeJavaUpgradeCode('populateDoseAndRoute');

/* study-16.22-16.23.sql */

ALTER TABLE study.DoseAndRoute DROP CONSTRAINT DoseAndRoute_Dose_Route_ProductId;
ALTER TABLE study.DoseAndRoute ADD CONSTRAINT DoseAndRoute_Container_Dose_Route_ProductId UNIQUE (Container, Dose, Route, ProductId);

/* study-16.23-16.24.sql */

SELECT core.executeJavaUpgradeCode('updateDateIndex');

/* study-16.24-16.25.sql */

ALTER TABLE study.AssaySpecimen ADD COLUMN SampleQuantity DOUBLE PRECISION;
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleUnits VARCHAR(5);

/* study-16.25-16.26.sql */

ALTER TABLE study.DoseAndRoute DROP COLUMN Label;

/* study-16.26-16.27.sql */

CREATE TABLE study.StudyDesignChallengeTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignchallengetypes PRIMARY KEY (Container, Name)
);