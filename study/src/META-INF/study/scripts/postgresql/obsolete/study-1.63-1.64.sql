--
-- refactor Visit, split VisitId into two different keys
--
-- VisitId --> VisitSequenceId
-- VisitId --> VisitRowId
--

-- in with the new
ALTER TABLE study.Visit ADD COLUMN RowId SERIAL;
ALTER TABLE study.Visit DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,RowId);
ALTER TABLE study.Visit ADD COLUMN SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.Visit ADD COLUMN SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0;
UPDATE study.Visit SET SequenceNumMin=VisitId, SequenceNumMax=VisitId;

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD COLUMN VisitRowId INT4;

UPDATE study.VisitMap
SET VisitRowId = (
    SELECT V.RowId
    FROM study.Visit V
    WHERE VisitMap.Container = V.Container AND VisitMap.VisitId = V.VisitId);

ALTER TABLE study.VisitMap DROP COLUMN VisitId;

ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);

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
    VisitDate TIMESTAMP
    );
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);


--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD COLUMN SequenceNum Numeric(15,4);
UPDATE study.StudyData SET SequenceNum=VisitId;
--ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE (Container, DatasetId, SequenceNum, ParticipantId);

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;
