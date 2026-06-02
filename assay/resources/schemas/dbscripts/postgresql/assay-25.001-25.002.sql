/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- This index overlaps with uq_platesetedge_fromplate_toplate
DROP INDEX assay.ix_platesetedge_fromplatesetid;
-- This index overlaps with uq_wellgroup_plateid_typename_name
DROP INDEX assay.ix_wellgroup_plateid;
-- This index overlaps with uq_well_plateid_row_col
DROP INDEX assay.ix_well_plateid;
-- This index overlaps with uq_plate_container_name_template
DROP INDEX assay.ix_plate_container;
