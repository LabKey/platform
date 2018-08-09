CREATE TABLE core.ReportEngineMap
(
  EngineId INTEGER NOT NULL,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container),
  CONSTRAINT FK_ReportEngineMap_ReportEngines FOREIGN KEY (EngineId) REFERENCES core.ReportEngines (RowId)
);


