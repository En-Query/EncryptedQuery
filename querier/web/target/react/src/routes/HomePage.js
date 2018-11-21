import React from "react";
import { BrowserRouter, Route, Switch, Link } from "react-router-dom";
import logo from "../enqueryLogo.png";
import "../css/HomePage.css";
import { OverlayTrigger, Tooltip } from "react-bootstrap";

export default class HomePage extends React.Component {
  render() {
    return (
      <div className="HomePage">
        <header className="HomePage-header">
          <img src={logo} className="HomePage-logo" alt="logo" />
          <h1 className="HomePage-title">Encrypted Query</h1>
        </header>
        <hr />

        <ul>
          <li>
            <Link to="/querier">Home</Link>
          </li>

          <li>
            <Link to="/querier/createqueryschema">Create Query Schema</Link>
          </li>

          <li>
            <Link to="/querier/createquery">Create Query</Link>
          </li>

          <li>
            <Link to="/querier/querystatus">Query Status</Link>
          </li>

          <li>Schedule Query</li>

          <li>Query Schedules Status</li>

          <li>Query Results</li>
        </ul>

        <hr />
      </div>
    );
  }
}
