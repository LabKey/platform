--
-- refactor Visit, split VisitId into two different keys
--
-- VisitId --> VisitSequenceId
-- VisitId --> VisitRowId
--

-- in with the new
ALTER TABLE study.Visit ADD RowId INT IDENTITY(1,1) NOT NULL;
ALTER TABLE study.Visit DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,RowId);
ALTER TABLE study.Visit ADD SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.Visit ADD SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0;
go
UPDATE study.Visit SET SequenceNumMin=VisitId, SequenceNumMax=VisitId;
go

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD VisitRowId INT NOT NULL DEFAULT -1;
go
UPDATE study.VisitMap
SET VisitRowId = (
    SELECT V.RowId
    FROM study.Visit V
    WHERE VisitMap.Container = V.Container AND VisitMap.VisitId = V.VisitId)
FROM study.VisitMap VisitMap
go
ALTER TABLE study.VisitMap DROP COLUMN VisitId;
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);
go

--
-- fix up ParticipantVisit
--

DROP TABLE study.ParticipantVisit;
CREATE TABLE study.ParticipantVisit
    (
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId int NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate DATETIME NULL
    );
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
go

--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD SequenceNum Numeric(15,4);
go
UPDATE study.StudyData SET SequenceNum=VisitId;
go
ALTER TABLE study.StudyData DROP AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, SequenceNum, ParticipantId);
go

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;
go