/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- For samples, incremental materialized-view updates filter exp.material by (CpasType, Modified) to find rows changed
-- since modification began. This index allows for the query to avoid a full table scan.
CREATE INDEX IX_Material_CpasType_Modified ON exp.material (CpasType, Modified);
