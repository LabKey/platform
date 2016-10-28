/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* study-14.10-14.11.sql */

ALTER TABLE study.StudyDesignAssays ADD Target VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Methodology VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Category VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetFunction VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LeadContributor VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Contact VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Summary TEXT;
ALTER TABLE study.StudyDesignAssays ADD Keywords TEXT;

ALTER TABLE study.StudyDesignLabs ADD PI VARCHAR(200);
ALTER TABLE study.StudyDesignLabs ADD Description TEXT;
ALTER TABLE study.StudyDesignLabs ADD Summary TEXT;
ALTER TABLE study.StudyDesignLabs ADD Institution VARCHAR(200);

/* study-14.11-14.12.sql */

ALTER TABLE study.visit ALTER COLUMN protocolday DROP NOT NULL;
ALTER TABLE study.visit ALTER COLUMN protocolday SET DEFAULT NULL;

/* study-14.12-14.13.sql */

DROP TABLE study.VisitTag;
CREATE TABLE study.VisitTag
(
  Name VARCHAR(200) NOT NULL,
  Caption VARCHAR(200) NOT NULL,
  Description TEXT,
  SingleUse BOOLEAN NOT NULL DEFAULT false,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Name_Container PRIMARY KEY (Name, Container)
);

/* study-14.13-14.14.sql */

ALTER TABLE study.StudySnapshot ADD ModifiedBy USERID;
ALTER TABLE study.StudySnapshot ADD Modified TIMESTAMP;

UPDATE study.StudySnapshot SET ModifiedBy = CreatedBy;
UPDATE study.StudySnapshot SET Modified = Created;

/* study-14.14-14.15.sql */

CREATE TABLE study.VisitTagMap
(
  RowId     SERIAL NOT NULL,
  VisitTag  CHARACTER VARYING(200) NOT NULL,
  VisitId   INTEGER NOT NULL,
  CohortId  INTEGER,
  Container ENTITYID NOT NULL,
  CONSTRAINT PK_VisitTagMap PRIMARY KEY (Container, RowId),
  CONSTRAINT VisitTagMap_Container_VisitTag_Key UNIQUE (Container, VisitTag, VisitId, CohortId)
);

/* study-14.15-14.16.sql */

CREATE UNIQUE INDEX VisitTagMap_container_tag_visit_idx ON study.VisitTagMap (Container, VisitTag, VisitId) WHERE CohortId IS NULL;

/* study-14.16-14.17.sql */

ALTER TABLE study.StudyDesignAssays ADD TargetType VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetSubtype VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Editorial TEXT;

/* study-14.17-14.18.sql */

ALTER TABLE study.StudyDesignAssays RENAME COLUMN Target TO Type;
ALTER TABLE study.StudyDesignAssays RENAME COLUMN Methodology TO Platform;

ALTER TABLE study.StudyDesignAssays ADD AlternateName VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Lab VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LabPI VARCHAR(200);