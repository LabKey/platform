/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- GitHub Issue 1499: Reset specimen base-column storage names that were uniquified with a numeric suffix but never became a physical column
UPDATE exp.propertydescriptor
SET storagecolumnname = exp.propertydescriptor.name
FROM exp.propertydomain pdm, exp.domaindescriptor dd
WHERE pdm.propertyid = exp.propertydescriptor.propertyid
  AND dd.domainid = pdm.domainid
  AND LOWER(dd.storageschemaname) = 'specimentables'
  AND LOWER(exp.propertydescriptor.storagecolumnname) = LOWER(exp.propertydescriptor.name) + '1';
