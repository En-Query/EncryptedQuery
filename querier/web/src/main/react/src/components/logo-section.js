import React from "react";
import { BrowserRouter, Route, Switch, Link } from "react-router-dom";
import logo from "../enqueryLogo.png";
import "../css/LogoSection.css";

export default class LogoSection extends React.Component {
  render() {
    return (
      <div className="LogoSection">
        <header className="LogoSection-header">
          <img src={logo} className="LogoSection-logo" alt="logo" />
          <h1 className="LogoSection-title">Encrypted Query</h1>
        </header>
      </div>
    );
  }
}
