/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

CREATE SCHEMA prop;
GO

--
-- NOTE: Bug in PropertyManager means NULL can't be passed for ObjectId, Category, etc. right now
--
-- ObjectId: EntityId or ContainerId this setting applies to
--     Generally only global User settings would use NULL ObjectId
-- Category: Category can be used to group related properties,
--     Category can be NULL if the Key field is reasonably unique/descriptive
-- UserId: Modules may use NULL UserId for general configuration
--
CREATE TABLE prop.PropertySets
(
    "Set" INT IDENTITY(1,1),
    ObjectId UNIQUEIDENTIFIER NULL,  -- e.g. EntityId or ContainerID
    Category VARCHAR(255) NULL,      -- e.g. "org.labkey.api.MailingList", may be NULL
    UserId USERID,

    CONSTRAINT PK_PropertySet PRIMARY KEY CLUSTERED ("Set"),
    CONSTRAINT UQ_PropertySet UNIQUE (ObjectId, UserId, Category)
);

CREATE TABLE prop.Properties
(
    "Set" INT NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Value VARCHAR(2000) NOT NULL,

    CONSTRAINT PK_Properties PRIMARY KEY CLUSTERED ("Set", Name)
);

GO

CREATE PROCEDURE prop.Property_setValue(@Set INT, @Name VARCHAR(255), @Value VARCHAR(2000)) AS
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