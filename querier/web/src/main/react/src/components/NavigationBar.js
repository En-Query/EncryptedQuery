import React from "react";
import { Menu } from "semantic-ui-react";
import { Link } from "react-router-dom";
import "semantic-ui-css/semantic.min.css";

export default class VerticalNavBar extends React.Component {
  state = {};

  handleItemCLick = name => this.setState({ activeItem: name });

  render() {
    const { activeItem } = this.state || {};

    return (
      <div>
        <Menu>
          <Menu.Item header>EnQuery</Menu.Item>
          <Menu.Item
            as={Link}
            to="/"
            name="home"
            active={activeItem === "home"}
            onClick={this.handleItemClick}
          >
            Home
          </Menu.Item>
          <Menu.Item
            as={Link}
            to="/createQuerySchema"
            name="createQuerySchema"
            active={activeItem === "createQuerySchema"}
            onClick={this.handleItemClick}
          >
            Create Query Schema
          </Menu.Item>

          <Menu.Item
            as={Link}
            to="/createquery"
            name="createQuery"
            active={activeItem === "createQuery"}
            onClick={this.handleItemClick}
          >
            Create Query
          </Menu.Item>

          <Menu.Item
            as={Link}
            to="/querystatus"
            name="queryStatus"
            active={activeItem === "queryStatus"}
            onClick={this.handleItemClick}
          >
            Query Status
          </Menu.Item>
        </Menu>
      </div>
    );
  }
}
