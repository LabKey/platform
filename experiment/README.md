# LabKey Experiment Module

The Experiment module defines the XAR (eXperimental 
ARchive) file format for importing and exporting experiment data and 
annotations, and allows user-defined custom annotations for specialized 
protocols and data.

### LabKey React pages

This module is setup to use `webpack` to build client application pages, mostly for developing 
with `React` components and `@labkey/components` shared components. The artifacts will be generated 
and placed into the standard LabKey view locations to make the pages available to the server through 
the `experiment` controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will 
be placed in the `experiment/resources/views` directory and the generated JS/CSS artifacts will be 
placed in the `experiment/resources/web/experiment/gen` directory.

For further details about developing React based pages see the README doc in `/server/webpack`:
"LabKey React Page Development". 