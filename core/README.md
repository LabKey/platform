# LabKey Core Module

The Core module provides central services such as login, security, administration, folder management, 
user management, module upgrade, file attachments, analytics, and portal page management.

### LabKey React pages

This module is setup to use `webpack` to build client application pages, mostly for developing 
with `React` components and `@labkey/components` shared components. The artifacts will be generated 
and placed into the standard LabKey view locations to make the pages available to the server through 
the `core` controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will 
be placed in the `core/resources/views` directory and the generated JS/CSS artifacts will be 
placed in the `core/resources/web/assay/gen` directory.

For further details about developing React based pages see the README doc in `/server/webpack`:
"LabKey React Page Development".