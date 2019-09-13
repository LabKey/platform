# Summary
This is a demo LabKey ReactJS module. This module demonstrates how to render a React application within the LabKey framework 
including keeping the standard LabKey header and header menus.

<a name="gettingStarted"></a>
# Getting Started
Build this module using the [LabKey Gradle build]. This will install necessary packages, generate resources and put resources in correct LabKey locations.

There are two example React pages at
- http://localhost:8080/labkey/home/demo-helloWorld.view?
- http://localhost:8080/labkey/home/demo-todoList.view?

<a name="functionality"></a>
# Functionality Overview
The compilation and packaging of this module, including the NPM/webpack build, is done with the standard Gradle build. 
Gradle will use the Node and NPM version defined in \<projectHome\>/gradle.properties to run the "build-prod" script defined in package.json.
(Note: Gradle does not install Node/NPM globally. To run NPM commands outside of Gradle you will need to have Node/NPM installed 
locally.)

The webpack production build will compile TypeScript and JavaScript files, as well as CSS and SCSS files, and bundle them independently 
for each webpack entry point.  The bundles are placed in the appropriate LabKey directory for web resources.  The production build will also 
generate the necessary LabKey HTML files, including the containing elements for the React apps, as well as the necessary view.xml files 
to make the bundled React and CSS files available to the appropriate LabKey React pages.

Note: To run NPM commands outside the Gradle build you will need to have Node/NPM installed locally.

<a name="devServer"></a>
## Development Server
This module includes a webpack development server to help with rapid development.  The server is setup for Hot Module Replacement, 
to allow updates made to TypeScript, JavaScript, CSS and SCSS files to take effect on the page without manual builds or page refreshes. To 
start the server, from the command line run "npm start", then navigate to either of the appropriate development pages.
- http://localhost:8080/labkey/home/demo-helloWorldDev.view?
- http://localhost:8080/labkey/home/demo-todoListDev.view?

<a name="jest"></a>
## Jest Tests
This module is setup to run Jest tests, including using Enzyme and Jest Snapshot testing. There is an example Jest test with 
snapshot in the HelloWorld module.  Jest tests can be run using "npm run test" or they can be run directly in IntelliJ.

    
[LabKey Gradle build]: https://www.labkey.org/Documentation/wiki-page.view?name=gradleBuild    