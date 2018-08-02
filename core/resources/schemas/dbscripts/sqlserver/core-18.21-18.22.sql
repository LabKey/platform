
UPDATE core.principals SET type = 'g' WHERE name = 'Developers' AND userid < 0;

EXEC core.executeJavaUpgradeCode 'updateDevelopersGroup';