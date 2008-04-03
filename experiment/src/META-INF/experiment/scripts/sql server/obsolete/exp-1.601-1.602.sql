CREATE TABLE exp.ActiveMaterialSource (
	Container ENTITYID NOT NULL,
	MaterialSourceLSID LSIDtype NOT NULL,
	CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
	CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container)
			REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID)
			REFERENCES exp.MaterialSource(LSID)
)
GO

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.*, dd.Project FROM exp.MaterialSource ms LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri
GO