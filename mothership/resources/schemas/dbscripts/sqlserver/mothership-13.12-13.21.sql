-- Add DESCRIPTION column to unique constraint on tble SoftwareRelease. Support for enhancment 18609

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT UQ_SoftwareRelease
GO

ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT UQ_SoftwareRelease UNIQUE
(
	Container,
	SVNRevision,
	SVNURL,
	Description
)