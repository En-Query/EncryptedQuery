import PropTypes from "prop-types";
import React, { Component } from "react";
import "semantic-ui-css/semantic.min.css";
import { Link } from "react-router-dom";
import {
  Button,
  Container,
  Grid,
  Header,
  Icon,
  Image,
  List,
  Menu,
  Responsive,
  Segment,
  Sticky,
  Sidebar,
  Visibility
} from "semantic-ui-react";

const getWidth = () => {
  const isSSR = typeof window === "undefined";

  return isSSR ? Responsive.onlyTablet.minWidth : window.innerWidth;
};

const HomepageHeading = ({ mobile }) => <Container text />;

HomepageHeading.propTypes = {
  mobile: PropTypes.bool
};

class DesktopContainer extends Component {
  state = {};

  handleItemClick = (e, { name }) => this.setState({ activeItem: name });

  render() {
    const { children } = this.props;
    const { fixed, activeItem } = this.state;

    return (
      <Responsive getWidth={getWidth} minWidth={Responsive.onlyTablet.minWidth}>
        <Sticky>
          <Menu fixed="top" inverted>
            <Container>
              <Menu.Item
                as={Link}
                to="/"
                name="home"
                active={activeItem === "home"}
                onClick={this.handleItemClick}
              >
                EnQuery
              </Menu.Item>
              <Menu.Item
                as={Link}
                to="/versions"
                name="versions"
                active={activeItem === "versions"}
                onClick={this.handleItemClick}
                style={{ textDecoration: "underline" }}
              >
                2.2.0
              </Menu.Item>

              <Menu.Menu position="right">
                <Menu.Item
                  as={Link}
                  to="/overview"
                  name="overview"
                  active={activeItem === "overview"}
                  onClick={this.handleItemClick}
                  position="right"
                >
                  Docs
                </Menu.Item>
                <Menu.Item
                  as={Link}
                  to="/offline"
                  name="offline"
                  active={activeItem === "admin"}
                  onClick={this.handleItemClick}
                  position="right"
                >
                  Offline Mode
                </Menu.Item>
                <Menu.Item
                  className="menu_color_white"
                  position="right"
                  href="https://github.com/En-Query/EncryptedQuery"
                  target="_blank"
                >
                  <Icon name="github" size="big" />
                </Menu.Item>
              </Menu.Menu>
            </Container>
          </Menu>
        </Sticky>
        <HomepageHeading />

        {children}
      </Responsive>
    );
  }
}

DesktopContainer.propTypes = {
  children: PropTypes.node
};

const ResponsiveContainer = ({ children }) => (
  <div>
    <DesktopContainer>{children}</DesktopContainer>{" "}
  </div>
);

ResponsiveContainer.propTypes = {
  children: PropTypes.node
};

const PageHeading = () => <ResponsiveContainer />;
export default PageHeading;
