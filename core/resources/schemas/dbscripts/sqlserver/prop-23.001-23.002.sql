ALTER TABLE prop.properties DROP CONSTRAINT PK_Properties;
GO

ALTER TABLE prop.properties ALTER COLUMN Name NVARCHAR(400) NOT NULL;
ALTER TABLE prop.properties ADD CONSTRAINT PK_Properties PRIMARY KEY CLUSTERED ("Set", Name);

GO
