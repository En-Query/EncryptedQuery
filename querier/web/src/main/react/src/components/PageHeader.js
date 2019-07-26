import PropTypes from "prop-types";
import React, { Component } from "react";
import "semantic-ui-css/semantic.min.css";
import { Link } from "react-router-dom";
import HeaderLogo from "../ENQUERY_final_RGB.png";
import styled from "styled-components";
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
  Sidebar,
  Visibility
} from "semantic-ui-react";
import "../css/styles.css";

const getWidth = () => {
  const isSSR = typeof window === "undefined";

  return isSSR ? Responsive.onlyTablet.minWidth : window.innerWidth;
};

const CustomSegment = styled(Segment)`
  &&& {
    background-color: gray;
  }
`;

const HomepageHeading = ({ mobile }) => (
  <Container text>
    <Header
      as="h1"
      content="Encrypted Query"
      inverted
      style={{
        fontSize: mobile ? "2em" : "4em",
        fontWeight: "normal",
        marginBottom: 0,
        marginTop: mobile ? "1.5em" : "3em"
      }}
    >
      <Image
        src={HeaderLogo}
        size="massive"
        style={{ width: "360px", height: "auto" }}
      />
    </Header>
    <Header
      as="h2"
      content="Encrypted Query"
      inverted
      style={{
        fontSize: mobile ? "1.5em" : "1.7em",
        fontWeight: "normal",
        marginTop: mobile ? "0.5em" : "1.5em"
      }}
    />
  </Container>
);

HomepageHeading.propTypes = {
  mobile: PropTypes.bool
};

class DesktopContainer extends Component {
  state = {};

  hideFixedMenu = () => this.setState({ fixed: false });
  showFixedMenu = () => this.setState({ fixed: true });
  handleItemClick = (e, { name }) => this.setState({ activeItem: name });

  render() {
    const { children } = this.props;
    const { fixed, activeItem } = this.state;

    return (
      <Responsive getWidth={getWidth} minWidth={Responsive.onlyTablet.minWidth}>
        <Visibility
          once={false}
          onBottomPassed={this.showFixedMenu}
          onBottomPassedReverse={this.hideFixedMenu}
        >
          <CustomSegment
            textAlign="center"
            style={{ minHeight: 500, padding: "0em 0em" }}
            vertical
          >
            <Menu
              fixed={fixed ? "top" : null}
              inverted={fixed}
              pointing={!fixed}
              secondary={!fixed}
              size="large"
            >
              <Container>
                <Menu.Item header>EnQuery</Menu.Item>
                <Menu.Item
                  className="menu_color_white"
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
                  className="menu_color_white"
                  to="/createQuerySchema"
                  name="createQuerySchema"
                  active={activeItem === "createQuerySchema"}
                  onClick={this.handleItemClick}
                >
                  Create Query Schema
                </Menu.Item>

                <Menu.Item
                  as={Link}
                  className="menu_color_white"
                  to="/createquery"
                  name="createQuery"
                  active={activeItem === "createQuery"}
                  onClick={this.handleItemClick}
                >
                  Create Query
                </Menu.Item>

                <Menu.Item
                  as={Link}
                  className="menu_color_white"
                  to="/querystatus"
                  name="queryStatus"
                  active={activeItem === "queryStatus"}
                  onClick={this.handleItemClick}
                >
                  Query Status
                </Menu.Item>
                <Menu.Menu position="right">
                  <Menu.Item
                    className="menu_color_white"
                    position="right"
                    href="https://github.com/En-Query/EncryptedQuery"
                    target="_blank"
                  >
                    <Icon name="github" size="big" />
                  </Menu.Item>
                                    <Menu.Item
                    className="menu_color_white"
                    as={Link}
                    to="/versions"
                    name="versions"
                    active={activeItem === "versions"}
                    onClick={this.handleItemClick}
                    position="right"
                    style={{ textDecoration: "underline" }}
                  >
                    2.1.4
                  </Menu.Item>
                </Menu.Menu>
              </Container>
            </Menu>
            <HomepageHeading />
          </CustomSegment>
        </Visibility>

        {children}
      </Responsive>
    );
  }
}

DesktopContainer.propTypes = {
  children: PropTypes.node
};

class MobileContainer extends Component {
  state = {};

  handleSidebarHide = () => this.setState({ sidebarOpened: false });
  handleItemClick = (e, { name }) => this.setState({ activeItem: name });
  handleToggle = () => this.setState({ sidebarOpened: true });

  render() {
    const { children } = this.props;
    const { sidebarOpened, activeItem } = this.state;

    return (
      <Responsive
        as={Sidebar.Pushable}
        getWidth={getWidth}
        maxWidth={Responsive.onlyMobile.maxWidth}
      >
        <Sidebar
          as={Menu}
          animation="push"
          inverted
          onHide={this.handleSidebarHide}
          vertical
          visible={sidebarOpened}
        >
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
            to="/createqueryschema"
            name="createqueryschema"
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
        </Sidebar>

        <Sidebar.Pusher dimmed={sidebarOpened}>
          <Segment
            inverted
            textAlign="center"
            style={{ minHeight: 350, padding: "1em 0em" }}
            vertical
          >
            <Container>
              <Menu inverted pointing secondary size="large">
                <Menu.Item onClick={this.handleToggle}>
                  <Icon name="sidebar" />
                </Menu.Item>
              </Menu>
            </Container>
            <HomepageHeading mobile />
          </Segment>

          {children}
        </Sidebar.Pusher>
      </Responsive>
    );
  }
}

MobileContainer.propTypes = {
  children: PropTypes.node
};

const ResponsiveContainer = ({ children }) => (
  <div>
    <DesktopContainer>{children}</DesktopContainer>{" "}
    <MobileContainer>{children}</MobileContainer>
  </div>
);

ResponsiveContainer.propTypes = {
  children: PropTypes.node
};

const PageHeading = () => <ResponsiveContainer />;
export default PageHeading;
