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