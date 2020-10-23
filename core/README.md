# LabKey Core Module

The Core module provides central services such as login, security, administration, folder management, 
user management, module upgrade, file attachments, analytics, and portal page management.

### LabKey React pages

This module is setup to use the [@labkey/build] `webpack` configuration to build client application 
pages, mostly for developing with `React` components and `@labkey/components` shared components. 
The artifacts will be generated and placed into the standard LabKey view locations to make the pages 
available to the server through the `core` controller. The generated `<entryPoint>.html` and 
`<entryPoint>.view.xml` files will be placed in the `core/resources/views` directory and the generated 
JS/CSS artifacts will be placed in the `core/resources/web/core/gen` directory.

For further details about developing React based pages in this module see the following documentation:
[LabKey React Page Development]. 

[LabKey React Page Development]: https://github.com/LabKey/labkey-ui-components/blob/master/packages/build/webpack/README.md
[@labkey/build]: https://github.com/LabKey/labkey-ui-components/tree/master/packages/build