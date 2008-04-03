ALTER TABLE core.Containers ADD 
    CaBIGPublished BIT NOT NULL DEFAULT 0
GO

CREATE TABLE core.ContainerAliases
	(
	Path VARCHAR(255) NOT NULL,
	ContainerId ENTITYID NOT NULL,

	CONSTRAINT UK_ContainerAliases_Paths UNIQUE (Path),
	CONSTRAINT FK_ContainerAliases_Containers FOREIGN KEY (ContainerId) REFERENCES core.Containers(EntityId)
	)
GO
