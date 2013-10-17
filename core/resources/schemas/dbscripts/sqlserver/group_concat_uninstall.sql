/*

  Copyright (c) 2011-2013 opcthree

  Simple uninstall script for SQL Server GROUP_CONCAT CLR functions, http://groupconcat.codeplex.com/

 */

IF EXISTS ( SELECT  *
            FROM    sys.objects
            WHERE   object_id = OBJECT_ID(N'core.[GROUP_CONCAT]')
                    AND type = N'AF' ) 
    DROP AGGREGATE core.[GROUP_CONCAT]
GO

IF EXISTS ( SELECT  *
            FROM    sys.objects
            WHERE   object_id = OBJECT_ID(N'core.[GROUP_CONCAT_D]')
                    AND type = N'AF' ) 
    DROP AGGREGATE core.[GROUP_CONCAT_D]
GO

IF EXISTS ( SELECT  *
            FROM    sys.objects
            WHERE   object_id = OBJECT_ID(N'core.[GROUP_CONCAT_DS]')
                    AND type = N'AF' ) 
    DROP AGGREGATE core.[GROUP_CONCAT_DS]
GO

IF EXISTS ( SELECT  *
            FROM    sys.objects
            WHERE   object_id = OBJECT_ID(N'core.[GROUP_CONCAT_S]')
                    AND type = N'AF' ) 
    DROP AGGREGATE core.[GROUP_CONCAT_S]
GO

IF EXISTS ( SELECT  *
            FROM    sys.assemblies asms
            WHERE   asms.name = N'GroupConcat'
                    AND is_user_defined = 1 ) 
    DROP ASSEMBLY [GroupConcat]
GO

--EO uninstall
