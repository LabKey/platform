-- Migrate login attempt settings from the compliance module's property store to core authentication settings.
SELECT core.executeJavaUpgradeCode('migrateLoginAttemptSettings');
