-------------------------------------------------------------------------------------------------------------------------------
-- Turn advanced options on
EXEC sys.sp_configure @configname = 'show advanced options', @configvalue = 1 ;
GO
RECONFIGURE WITH OVERRIDE ;
GO
-- Enable CLR
EXEC sys.sp_configure @configname = 'clr enabled', @configvalue = 1 ;
GO
RECONFIGURE WITH OVERRIDE ;
GO
