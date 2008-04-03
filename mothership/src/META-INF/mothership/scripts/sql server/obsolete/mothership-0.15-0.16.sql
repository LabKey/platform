CREATE VIEW mothership.ServerInstallationWithSession AS
    SELECT
        i.ServerInstallationId,
        i.ServerInstallationGUID,
        i.Note,
        i.Container,
        i.SystemDescription,
        i.LogoLink,
        i.OrganizationName,
        i.SystemShortName,
        i.ServerIP,
        i.ServerHostName,
        s.LastKnownTime
    FROM
        mothership.ServerInstallation i,
        ( SELECT MAX(lastknowntime) AS LastKnownTime, ServerInstallationId
            FROM mothership.ServerSession
            GROUP BY ServerInstallationId ) s
    WHERE
        i.ServerInstallationId = s.ServerInstallationId
GO

CREATE TABLE mothership.SoftwareRelease
	(
	ReleaseId INT IDENTITY(1,1) NOT NULL,
	SVNRevision INT NOT NULL,
	Description VARCHAR(50) NOT NULL,
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_SoftwareRelease PRIMARY KEY (ReleaseId),
	CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, SVNRevision)
	)
GO


