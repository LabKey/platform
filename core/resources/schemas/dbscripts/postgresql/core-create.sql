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
    SELECT Principals.Name AS Email, UsersData.*, Principals.Active
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
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
            ExpirationDate = NEW.ExpirationDate
        WHERE UserId = NEW.UserId;

CREATE VIEW core.ActiveUsers AS
    SELECT *
    FROM core.Users
    WHERE Active=true;


