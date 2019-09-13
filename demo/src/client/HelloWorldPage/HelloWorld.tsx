import * as React from 'react'
import "./helloWorld.scss";

export class App extends React.PureComponent<any, any> {

    render() {
        return (
            <p>
                <span className='world-highlight'>Hello World!</span> This is a simple test page to show a LabKey HTML view built with React.
            </p>
        )
    }
}
