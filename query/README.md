# LabKey Query Module

The Query module provides a user interface for viewing sql queries and tables of the database and results of those queries.

### LabKey React pages

This module is setup to use `webpack` to build client application pages, mostly for developing 
with `React` components and `@labkey/components` shared components. The artifacts will be generated 
and placed into the standard LabKey view locations to make the pages available to the server through 
the `query` controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will 
be placed in the `query/resources/views` directory and the generated JS/CSS artifacts will be 
placed in the `query/resources/web/experiment/gen` directory.

For further details about developing React based pages see the README doc in `/server/webpack`:
"LabKey React Page Development".