
UPDATE prop.properties SET value = E'^optionalMessage^\n\n' || value
    WHERE set IN (SELECT set FROM prop.propertysets WHERE category = 'emailTemplateProperties')
	  AND (name = 'org.labkey.api.security.SecurityManager$RegistrationEmailTemplate/body'
	  OR name = 'org.labkey.api.security.SecurityManager$RegistrationAdminEmailTemplate/body');

