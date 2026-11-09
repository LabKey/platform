/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- This index overlaps with uq_conditionalformat_propertyid_sortorder
DROP INDEX exp.idx_conditionalformat_propertyid;
-- This index overlaps with uq_alias_name
DROP INDEX exp.ix_alias_name;
-- This index overlaps with uq_dataclass_container_name
DROP INDEX exp.ix_dataclass_container;
-- This index overlaps with uq_protocolappparam_ord
DROP INDEX exp.ix_protocolapplicationparameter_appid;
-- This index overlaps with uq_protocolparameter_ord
DROP INDEX exp.ix_protocolparameter_protocolid;
