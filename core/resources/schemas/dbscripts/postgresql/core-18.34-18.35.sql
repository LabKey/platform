
ALTER TABLE core.ReportEngineMap DROP CONSTRAINT PK_ReportEngineMap;
ALTER TABLE core.ReportEngineMap ADD EngineContext VARCHAR(64) NOT NULL DEFAULT 'report';
ALTER TABLE core.ReportEngineMap ADD CONSTRAINT PK_ReportEngineMap PRIMARY KEY (EngineId, Container, EngineContext);

SELECT core.executeJavaUpgradeCode('migrateSiteDefaultEngines');