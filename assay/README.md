# LabKey Assay Module

Provides services and other APIs that assay modules call to implement custom assay types. 
Also includes implementation of the General Purpose Assay Type (GPAT).

### LabKey React pages

This module is setup to use `webpack` to build client application pages, mostly for developing 
with `React` components and `@labkey/components` shared components. The artifacts will be generated 
and placed into the standard LabKey view locations to make the pages available to the server through 
the `assay` controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will 
be placed in the `assay/resources/views` directory and the generated JS/CSS artifacts will be 
placed in the `assay/resources/web/assay/gen` directory.

For further details about developing React based pages see the README doc in `/server/webpack`:
"LabKey React Page Development".