
ALTER TABLE core.ReportEngineMap DROP CONSTRAINT PK_ReportEngineMap;
ALTER TABLE core.ReportEngineMap ADD EngineContext NVARCHAR(64) NOT NULL DEFAULT 'report';
ALTER TABLE core.ReportEngineMap ADD CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container, EngineContext);

EXEC core.executeJavaUpgradeCode 'migrateSiteDefaultEngines';