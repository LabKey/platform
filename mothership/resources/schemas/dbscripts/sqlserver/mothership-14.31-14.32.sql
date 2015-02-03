ALTER TABLE mothership.ServerInstallation DROP CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID;
GO
ALTER TABLE mothership.ServerInstallation ALTER COLUMN ServerInstallationGUID NVARCHAR(1000) NOT NULL;
GO
ALTER TABLE mothership.ServerInstallation ADD CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID);