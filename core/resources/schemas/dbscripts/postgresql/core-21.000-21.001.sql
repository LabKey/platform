-- Rename 'MobileAppStudy' module to 'Response'
UPDATE core.sqlscripts SET modulename = 'Response' WHERE modulename = 'MobileAppStudy';
UPDATE core.modules SET name = 'Response', classname = 'org.labkey.response.ResponseModule'
    WHERE name = 'MobileAppStudy';
