-- Move compliance schema management to Signing module
UPDATE core.SqlScripts SET modulename = 'Signing' WHERE modulename = 'Compliance';
