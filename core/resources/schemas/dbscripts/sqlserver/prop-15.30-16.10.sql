/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* prop-15.32-15.33.sql */

EXEC core.fn_dropifexists 'Property_setValue', 'prop', 'PROCEDURE'
GO

-- When prop.Properties.Value was changed from varchar(255) to nvarchar(max), this proc was still at varchar(2000), which could
-- cause truncation with no warning on persisting values.
CREATE PROCEDURE prop.Property_setValue(@Set INT, @Name VARCHAR(255), @Value NVARCHAR(max)) AS
  BEGIN
    IF (@Value IS NULL)
      DELETE prop.Properties WHERE "Set" = @Set AND Name = @Name
    ELSE
      BEGIN
        UPDATE prop.Properties SET Value = @Value WHERE "Set" = @Set AND Name = @Name
        IF (@@ROWCOUNT = 0)
          INSERT prop.Properties VALUES (@Set, @Name, @Value)
      END
  END;

GO