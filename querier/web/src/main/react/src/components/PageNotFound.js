import React from "react";

import HomePage from "../routes/HomePage.js";

export default class PageNotFound extends React.Component {
  render() {
    return (
      <div>
        <div>
          <HomePage />
        </div>
        <div>
          <h2>This page does not exist.</h2>
          <p>
            This route doesn't exist, please choose a landing page from the
            above list
          </p>
        </div>
      </div>
    );
  }
}
