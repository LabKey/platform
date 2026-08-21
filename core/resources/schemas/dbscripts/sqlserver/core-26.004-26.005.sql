/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Migrate login attempt settings from the compliance module's property store to core authentication settings.
EXEC core.executeJavaUpgradeCode 'migrateLoginAttemptSettings';
