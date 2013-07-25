
CREATE TABLE study.StudyDesignImmunogenTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignimmunogentypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignGenes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesigngenes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignRoutes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignroutes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSubTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsubtypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSampleTypes
(
  Name VARCHAR(200) NOT NULL,
  PrimaryType VARCHAR(200) NOT NULL,
  ShortSampleCode VARCHAR(2) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsampletypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignUnits
(
  Name VARCHAR(3) NOT NULL, -- storage name
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignAssays
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Description TEXT,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignassays PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignLabs
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignlabs PRIMARY KEY (Container, Name)
);