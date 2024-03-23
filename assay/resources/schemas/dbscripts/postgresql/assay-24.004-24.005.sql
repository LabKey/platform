CREATE TABLE assay.Hit
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ProtocolId INT NOT NULL,
    ResultId INT NOT NULL,
    RunId INT NOT NULL,
    WellLsid VARCHAR(200) NOT NULL,

    CONSTRAINT PK_Plate PRIMARY KEY (RowId),
    CONSTRAINT FK_Hit_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_Protocol_ProtocolId FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId),
    CONSTRAINT FK_Run_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_Well_WellLsid FOREIGN KEY (WellLsid) REFERENCES assay.Well (Lsid),
    CONSTRAINT UQ_Hit_RunId_ResultId UNIQUE (RunId, ResultId)
);
