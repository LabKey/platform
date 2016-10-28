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

ALTER TABLE study.StudyDesignAssays ADD Target NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Methodology NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Category NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetFunction NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LeadContributor NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Contact NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Summary TEXT;
ALTER TABLE study.StudyDesignAssays ADD Keywords TEXT;

ALTER TABLE study.StudyDesignLabs ADD PI NVARCHAR(200);
ALTER TABLE study.StudyDesignLabs ADD Description TEXT;
ALTER TABLE study.StudyDesignLabs ADD Summary TEXT;
ALTER TABLE study.StudyDesignLabs ADD Institution NVARCHAR(200);

/* study-14.11-14.12.sql */

--ALTER TABLE study.visit ALTER protocolday DROP NOT NULL;
EXEC core.fn_dropifexists 'Visit', 'study', 'DEFAULT', 'ProtocolDay';
GO
ALTER TABLE study.Visit ALTER COLUMN ProtocolDay NUMERIC(15,4) NULL;
GO
ALTER TABLE study.Visit ADD DEFAULT NULL FOR ProtocolDay;

/* study-14.12-14.13.sql */

DROP TABLE study.VisitTag;
CREATE TABLE study.VisitTag
(
  Name NVARCHAR(200) NOT NULL,
  Caption NVARCHAR(200) NOT NULL,
  Description NVARCHAR(MAX),
  SingleUse BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Name_Container PRIMARY KEY (Name, Container),
);

/* study-14.13-14.14.sql */

ALTER TABLE study.StudySnapshot ADD ModifiedBy USERID;
ALTER TABLE study.StudySnapshot ADD Modified DATETIME;
GO

UPDATE study.StudySnapshot SET ModifiedBy = CreatedBy;
UPDATE study.StudySnapshot SET Modified = Created;
GO

/* study-14.14-14.15.sql */

CREATE TABLE study.VisitTagMap
(
  RowId     INT IDENTITY(1,1),
  VisitTag  NVARCHAR(200) NOT NULL,
  VisitId   INTEGER NOT NULL,
  CohortId  INTEGER,
  Container ENTITYID NOT NULL,
  CONSTRAINT PK_VisitTagMap PRIMARY KEY (Container, RowId),
  CONSTRAINT VisitTagMap_Container_VisitTag_Key UNIQUE (Container, VisitTag, VisitId, CohortId)
);

/* study-14.16-14.17.sql */

ALTER TABLE study.StudyDesignAssays ADD TargetType NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetSubtype NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Editorial NVARCHAR(MAX);

/* study-14.17-14.18.sql */

EXEC sp_rename 'study.StudyDesignAssays.Target', 'Type', 'COLUMN';
GO
EXEC sp_rename 'study.StudyDesignAssays.Methodology', 'Platform', 'COLUMN';
GO

ALTER TABLE study.StudyDesignAssays ADD AlternateName NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Lab NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LabPI NVARCHAR(200);