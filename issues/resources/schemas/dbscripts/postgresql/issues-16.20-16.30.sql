/* issues-16.20-16.21.sql */

ALTER TABLE issues.issuelistdef ADD COLUMN kind VARCHAR(200) NOT NULL DEFAULT 'IssueDefinition';

/* issues-16.21-16.22.sql */

SELECT core.executeJavaUpgradeCode('upgradeSpecialFields');

/* issues-16.22-16.23.sql */

DROP INDEX issues.IX_Issues_AssignedTo;
DROP INDEX issues.IX_Issues_Status;

-- delete obsolete fields from the issues.issues table
SELECT core.executeJavaUpgradeCode('dropLegacyFields');

/* issues-16.23-16.24.sql */

-- drop redundant properties from the issues provisioned tables
SELECT core.executeJavaUpgradeCode('dropRedundantProperties');