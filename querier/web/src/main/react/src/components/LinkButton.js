import React from 'react';
import PropTypes from 'prop-types';
import { BrowserRouter, Route, Redirect, Switch } from 'react-router-dom';


class LinkButton extends React.Component {
	  state = {
	    navigate: false
	  }

	  render() {
	    const { navigate } = this.state

	    // here is the important part
	    if (navigate) {
	      return <Redirect to="/querier/createquery" push={true} />
	    // ^^^^ when button is clicked it's redirected to this path
	    }
	   // ^^^^^^^^^^^^^^^^^^^^^^^

	    return (
	      <div>
	      <p> On Submit, will build query schema </p>
	        <button onClick={() => this.setState({ navigate: true })}>
	          Submit Schema & Selectors!
	        </button>
	      </div>
	    )
	  }
	}

export default LinkButton;
