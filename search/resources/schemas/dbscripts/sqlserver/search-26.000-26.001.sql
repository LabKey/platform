/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Upgrade Lucene to 10.3.2
EXEC core.executeJavaUpgradeCode 'reindex';
