ALTER TABLE mothership.ServerSession ADD UserCount INT
GO
ALTER TABLE mothership.ServerSession ADD ActiveUserCount INT
GO
ALTER TABLE mothership.ServerSession ADD ProjectCount INT
GO
ALTER TABLE mothership.ServerSession ADD ContainerCount INT
GO

ALTER TABLE mothership.ServerSession ADD AdministratorEmail NVARCHAR(100)
GO

