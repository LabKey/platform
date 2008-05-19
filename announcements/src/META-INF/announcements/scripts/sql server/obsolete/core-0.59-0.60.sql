/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
/*
   Once Announcements view is converted to a table (see cpas-0.58-0.59 script), get rid of
   Lists table, EntityTypes table, and ENTITYTYPE data type
*/

IF OBJECT_ID('EntityTypes','U') IS NOT NULL
    DROP TABLE EntityTypes
IF OBJECT_ID('Lists','U') IS NOT NULL
    DROP TABLE Lists
IF (SELECT COUNT(*) FROM systypes WHERE NAME = 'ENTITYTYPE') > 0
    EXEC sp_droptype 'ENTITYTYPE'
GO