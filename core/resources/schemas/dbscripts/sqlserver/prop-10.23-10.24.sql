
UPDATE prop.properties SET value = '^optionalMessage^' + CHAR(13) + CHAR(13) + value
    WHERE 'set' IN (SELECT 'set' FROM prop.propertysets WHERE category = 'emailTemplateProperties')
	  AND (name = 'org.labkey.api.security.SecurityManager$RegistrationEmailTemplate/body'
	  OR name = 'org.labkey.api.security.SecurityManager$RegistrationAdminEmailTemplate/body')
GO

