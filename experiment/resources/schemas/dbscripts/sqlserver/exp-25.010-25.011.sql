/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- This index overlaps with uq_conditionalformat_propertyid_sortorder
DROP INDEX idx_conditionalformat_propertyid ON exp.ConditionalFormat;
-- This index overlaps with uq_alias_name
DROP INDEX ix_alias_name ON exp.Alias;
-- This index overlaps with uq_dataclass_container_name
DROP INDEX ix_dataclass_container ON exp.DataClass;
-- This index overlaps with uq_protocolappparam_ord
DROP INDEX ix_protocolapplicationparameter_appid ON exp.ProtocolApplicationParameter;
-- This index overlaps with uq_protocolparameter_ord
DROP INDEX ix_protocolparameter_protocolid ON exp.ProtocolParameter;
