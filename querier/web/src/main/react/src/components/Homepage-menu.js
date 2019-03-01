import React from "react";
import { Link } from "react-router-dom";
import logo from "../enqueryLogo.png";

export default class HomeMenu extends React.Component {
  render() {
    return (
      <div>
        <hr />

        <ul>
          <li>
            <Link to="/">Home</Link>
          </li>

          <li>
            <Link to="/createqueryschema">Create Query Schema</Link>
          </li>

          <li>
            <Link to="/createquery">Create Query</Link>
          </li>

          <li>
            <Link to="/querystatus">Query Status</Link>
          </li>
        </ul>

        <hr />
      </div>
    );
  }
}
