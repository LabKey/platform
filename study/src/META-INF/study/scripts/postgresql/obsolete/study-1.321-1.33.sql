CREATE TABLE study.AssayRun
    (
	RowId SERIAL,
    AssayType VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_AssayRun PRIMARY KEY (RowId)
    );