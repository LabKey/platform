/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
CREATE VIEW core.Users AS
    SELECT p.Name AS Email, ud.*, p.Active, l.UserId IS NOT NULL AS HasPassword
    FROM core.Principals p
        INNER JOIN core.UsersData ud ON p.UserId = ud.UserId
        LEFT OUTER JOIN core.Logins l ON p.UserId = l.UserId
    WHERE Type = 'u';

CREATE OR REPLACE RULE Users_Update AS
    ON UPDATE TO core.Users DO INSTEAD
        UPDATE core.UsersData SET
            ModifiedBy = NEW.ModifiedBy,
            Modified = NEW.Modified,
            FirstName = NEW.FirstName,
            LastName = NEW.LastName,
            Phone = NEW.Phone,
            Mobile = NEW.Mobile,
            Pager = NEW.Pager,
            IM = NEW.IM,
            Description = NEW.Description,
            LastLogin = NEW.LastLogin,
            DisplayName = NEW.DisplayName,
            ExpirationDate = NEW.ExpirationDate,
            System = NEW.System,
            LastActivity = NEW.LastActivity
        WHERE UserId = NEW.UserId;

CREATE VIEW core.ActiveUsers AS
    SELECT *
    FROM core.Users
    WHERE Active=true;

CREATE VIEW core.ContainerPath AS
    WITH RECURSIVE container_hierarchy AS (
        SELECT
            EntityId,
            Name,
            Parent,
            Type,
            1 as Level,
            CAST('' AS VARCHAR(4000)) AS Path
        FROM core.Containers
        WHERE Parent IS NULL AND Type = 'normal'

        UNION ALL

        SELECT
            c.EntityId,
            c.Name,
            c.Parent,
            c.Type,
            ch.Level + 1,
            CAST(ch.Path || '/' || c.Name AS VARCHAR(4000)) AS Path
        FROM core.Containers c JOIN container_hierarchy ch ON c.Parent = ch.EntityId
    ) SELECT * from container_hierarchy


