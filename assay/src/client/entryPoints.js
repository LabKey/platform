/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
  apps: [
    {
      name: "assayTypeSelect",
      path: "./src/client/AssayTypeSelect",
      permissionClasses: [
        "org.labkey.api.assay.security.DesignAssayPermission",
      ],
      title: "New Assay Design",
    },
    {
      name: "plateTemplateDesigner",
      path: "./src/client/PlateTemplateDesigner",
      permissionClasses: [
        "org.labkey.api.security.permissions.InsertPermission",
        "org.labkey.api.assay.security.DesignAssayPermission",
      ],
      title: "Plate Template Designer",
    },
  ],
};
