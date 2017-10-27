/*
 * Copyright (c) 2017 LabKey Corporation
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

/* exp-17.20-17.21.sql */

ALTER TABLE exp.PropertyDescriptor ADD Phi NVARCHAR(20) NOT NULL DEFAULT 'NotPHI';
GO
UPDATE exp.PropertyDescriptor SET Phi='Limited' WHERE Protected='TRUE';
GO

/* exp-17.21-17.22.sql */

EXEC core.fn_dropifexists 'PropertyDescriptor', 'exp', 'COLUMN', 'Protected';
GO

/* exp-17.22-17.23.sql */

ALTER TABLE exp.PropertyDescriptor ADD RedactedText NVARCHAR(450) NULL;
GO

/* exp-17.23-17.24.sql */

ALTER TABLE exp.PropertyDescriptor ADD mvIndicatorStorageColumnName NVARCHAR(120);
GO

EXEC core.executeJavaUpgradeCode 'saveMvIndicatorStorageNames';