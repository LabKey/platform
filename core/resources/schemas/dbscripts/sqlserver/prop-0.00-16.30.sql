/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

/* prop-0.00-10.30.sql */

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

/* prop-12.10-12.20.sql */

-- Clean out any Properties that belong to a PropertySet that doesn't have a Container (ObjectId) anymore
DELETE FROM prop.properties WHERE "set" IN
  (SELECT "set" FROM prop.propertysets WHERE ObjectId NOT IN
    (SELECT EntityId FROM core.Containers));

-- Clean out any Properties that belong to a PropertySet that doesn't exist anymore
DELETE FROM prop.properties WHERE "set" NOT IN
  (SELECT "set" FROM prop.propertysets);

-- Clean out PropertySets that don't have a Container (ObjectId) anymore
DELETE FROM prop.propertysets WHERE ObjectId NOT IN
  (SELECT EntityId FROM core.Containers);

-- Create real FKs to prevent orphaning in the future
ALTER TABLE prop.properties
    ADD CONSTRAINT FK_Properties_Set FOREIGN KEY ("set") REFERENCES prop.PropertySets ("set");

ALTER TABLE prop.propertysets
    ADD CONSTRAINT FK_PropertySets_ObjectId FOREIGN KEY (ObjectId) REFERENCES core.Containers (EntityId);

/* prop-13.20-13.30.sql */

-- Add a column that specifies algorithm used to encrypt all values in this property set
ALTER TABLE prop.PropertySets
    ADD Encryption VARCHAR(100) NOT NULL DEFAULT 'None';

/* prop-13.30-14.10.sql */

-- Remove limit on value length
ALTER TABLE prop.Properties ALTER COLUMN Value NVARCHAR(MAX);

/* prop-15.30-16.10.sql */

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

/* prop-16.20-16.30.sql */

-- Add support for formatting Date-only columns, as opposed to DateTime columns, #26844. First, rename existing default format
-- property from defaultDateFormatString to defaultDateTimeFormatString
UPDATE prop.Properties SET Name = 'defaultDateTimeFormatString' WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateFormatString';

-- Now add values for defaultDateFormatString where defaultDateTimeFormatString has been defined. Extract the date-only
-- portion of the format string using a simple algorithm: assume that the first space separates date and time portion of
-- the format string, so truncate at the first space if present, otherwise use the whole string.
INSERT INTO prop.Properties
    SELECT [Set], 'defaultDateFormatString' AS Name, CASE WHEN CHARINDEX(' ', Value) > 0 THEN LEFT(Value, CHARINDEX(' ', Value) - 1) ELSE Value END AS Value
    FROM prop.Properties WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateTimeFormatString';

-- Re-split the old date-time formats into date-only formats using a slightly better algorithm: truncate at the first
-- space that follows the first "y" or "Y" in the format.

-- Delete the previous date-only formats
DELETE FROM prop.Properties WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateFormatString';

-- Now insert values for defaultDateFormatString where defaultDateTimeFormatString is defined, using the new approach
INSERT INTO prop.Properties
    SELECT [Set], 'defaultDateFormatString' AS Name,
        CASE WHEN CHARINDEX('y', LOWER(Value)) > 0 THEN
            CASE WHEN CHARINDEX(' ', Value, CHARINDEX('y', LOWER(Value))) > 0 THEN
                SUBSTRING(Value, 0, CHARINDEX(' ', Value, CHARINDEX('y', LOWER(Value))))
            ELSE
                Value
            END
        ELSE
            Value
        END AS Value
    FROM prop.Properties WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateTimeFormatString';