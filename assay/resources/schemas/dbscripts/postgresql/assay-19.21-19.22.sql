-- Repackage AssayDesignerRole from study to assay
UPDATE core.RoleAssignments SET Role = 'org.labkey.assay.security.AssayDesignerRole' WHERE Role = 'org.labkey.study.security.roles.AssayDesignerRole';
