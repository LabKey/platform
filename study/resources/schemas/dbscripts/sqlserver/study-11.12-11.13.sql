
-- named sets of normalization factors
CREATE TABLE study.ParticipantClassifications
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,

	Label NVARCHAR(200) NOT NULL,
	Type NVARCHAR(60) NOT NULL,
    Shared BIT,
    AutoUpdate BIT,

	-- for queries
    QueryName NVARCHAR(200),
    ViewName NVARCHAR(200),
    SchemaName NVARCHAR(50),

    -- for cohorts
    DatasetId INT,
    GroupProperty NVARCHAR(200),

    CONSTRAINT pk_participantClassifications PRIMARY KEY (RowId)
)
GO

-- represents a grouping category for a participant classification
CREATE TABLE study.ParticipantGroup
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,

	Label NVARCHAR(200) NOT NULL,
    ClassificationId INT NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId),
    CONSTRAINT fk_participantClassifications_classificationId FOREIGN KEY (ClassificationId) REFERENCES study.ParticipantClassifications (RowId)
)
GO

-- maps participants to participant groups
CREATE TABLE study.ParticipantGroupMap
(
    GroupId INT NOT NULL,
    ParticipantId NVARCHAR(32) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT pk_participantGroupMap PRIMARY KEY (GroupId, ParticipantId, Container),
    CONSTRAINT fk_participantGroup_groupId FOREIGN KEY (GroupId) REFERENCES study.ParticipantGroup (RowId),
    CONSTRAINT fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
)
GO
