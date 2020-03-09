# LabKey List Module

A List is a flexible, user-defined table that is defined 
and managed via the LabKey Server web UI. The schema of a list can be 
defined manually, created using the schema of another list as a template, 
or inferred from the contents of a data file. Lists can be linked via 
lookups and joins to create custom views that draw data from many sources. 
Populated lists can be exported and imported as archives for easy transfer 
between development, staging and production folders or servers.

### LabKey React pages

This module is setup to use `webpack` to build client application pages, mostly for developing 
with `React` components and `@labkey/components` shared components. The artifacts will be generated 
and placed into the standard LabKey view locations to make the pages available to the server through 
the `list` controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will 
be placed in the `list/resources/views` directory and the generated JS/CSS artifacts will be 
placed in the `list/resources/web/list/gen` directory.

For further details about developing React based pages in this module see the following documentation:
[LabKey React Page Development]. 

[LabKey React Page Development]: https://github.com/LabKey/platform/tree/develop/webpack