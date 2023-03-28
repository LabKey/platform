-- Compliance schema is now managed by the Signing module
UPDATE core.modules SET Schemas = NULL WHERE Name = 'Compliance';
