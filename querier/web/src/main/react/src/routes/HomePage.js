import React from "react";
import logo from "../enqueryLogo.png";
import "../css/LogoSection.css";
import HomeMenu from "../components/Homepage-menu";

export default class HomePage extends React.Component {
  render() {
    return (
      <div className="LogoSection">
        <header className="LogoSection-header">
          <img src={logo} className="LogoSection-logo" alt="logo" />
          <h1 className="LogoSection-title">Encrypted Query</h1>
        </header>
        <HomeMenu />
      </div>
    );
  }
}
