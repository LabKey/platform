/* issues-16.20-16.21.sql */

ALTER TABLE issues.issuelistdef ADD kind NVARCHAR(200) NOT NULL DEFAULT 'IssueDefinition';

/* issues-16.21-16.22.sql */

EXEC core.executeJavaUpgradeCode 'upgradeSpecialFields';

/* issues-16.22-16.23.sql */

DROP INDEX IX_Issues_AssignedTo ON issues.issues;
DROP INDEX IX_Issues_Status ON issues.issues;

-- Must drop default constraint before dropping column
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Priority'
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Created'
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Modified'

-- delete obsolete fields from the issues.issues table
EXEC core.executeJavaUpgradeCode 'dropLegacyFields';

/* issues-16.23-16.24.sql */

-- drop redundant properties from the issues provisioned tables
EXEC core.executeJavaUpgradeCode 'dropRedundantProperties';