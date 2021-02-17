-- Rename 'MobileAppStudy' module to 'Response'
UPDATE core.sqlscripts SET modulename = 'Response' WHERE modulename = 'MobileAppStudy';
UPDATE core.modules SET name = 'Response' WHERE name = 'MobileAppStudy';
UPDATE core.portalwebparts SET name = 'MyStudies Study Setup' WHERE name = 'Mobile App Study Setup';
