import PropTypes from "prop-types";
import React, { Component } from "react";
import { Link } from "react-router-dom";
import logo from "../enqueryLogo.png";
import HeaderLogo from "../ENQUERY_final_RGB.png";
import styled from "styled-components";
import {
  Button,
  Container,
  Divider,
  Grid,
  Header,
  Icon,
  Image,
  List,
  Menu,
  Responsive,
  Segment,
  Sidebar,
  Visibility,
  Sticky
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
      style={{
        fontSize: mobile ? "2em" : "4em",
        fontWeight: "normal",
        marginBottom: 0,
        marginTop: mobile ? "1.5em" : "3em"
      }}
    >
      <Image src={HeaderLogo} style={{ width: "360px", height: "auto" }} />
    </Header>
    <Header
      as="h2"
      content="Start by creating a Query Schema"
      inverted
      style={{
        fontSize: mobile ? "1.5em" : "1.7em",
        fontWeight: "normal",
        marginTop: mobile ? "0.5em" : "1.5em"
      }}
    />
    <Button
      primary
      size="huge"
      as={Link}
      name="createQuerySchema"
      to="/createqueryschema"
      style={{ marginTop: "30px" }}
    >
      Get Started
      <Icon name="right arrow" />
    </Button>
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
        <CustomSegment
          textAlign="center"
          style={{ minHeight: 700, padding: "0em 0em" }}
          vertical
        >
          <Sticky>
            <Menu inverted style={{ margin: 0 }} size="large">
              <Container>
                <Menu.Item
                  className="menu_color_white"
                  as={Link}
                  to="/"
                  name="home"
                  active={activeItem === "home"}
                  onClick={this.handleItemClick}
                >
                  EnQuery
                </Menu.Item>
                <Menu.Item
                  className="menu_color_white"
                  as={Link}
                  to="/createqueryschema"
                  name="createQuerySchema"
                  active={activeItem === "createQuerySchema"}
                  onClick={this.handleItemClick}
                >
                  Create Query Schema
                </Menu.Item>

                <Menu.Item
                  className="menu_color_white"
                  as={Link}
                  to="/createquery"
                  name="createQuery"
                  active={activeItem === "createQuery"}
                  onClick={this.handleItemClick}
                >
                  Create Query
                </Menu.Item>
                <Menu.Item
                  className="menu_color_white"
                  as={Link}
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
                    className="menu_color_white"
                    as={Link}
                    to="/offline"
                    name="offline"
                    active={activeItem === "offline"}
                    onClick={this.handleItemClick}
                    position="right"
                  >
                    Offline Mode
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
                    2.2.1
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
        </CustomSegment>
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
    <DesktopContainer>{children}</DesktopContainer>
    <MobileContainer>{children}</MobileContainer>
  </div>
);

ResponsiveContainer.propTypes = {
  children: PropTypes.node
};

const HomepageLayout = () => (
  <ResponsiveContainer>
    <Segment style={{ padding: "8em 0em" }} vertical>
      <Grid container stackable verticalAlign="middle">
        <Grid.Row>
          <Grid.Column width={8}>
            <Header as="h3" style={{ fontSize: "2em" }}>
              Ask a secret question | Get a secret answer
            </Header>
            <p style={{ fontSize: "1.33em" }}>
              Encrypted Query is designed to allow a user to query a remote
              database without revealing the contents of the query or the
              results from the database server.
            </p>
          </Grid.Column>
          <Grid.Column floated="right" width={6}>
            <Image bordered rounded size="large" src={HeaderLogo} />
          </Grid.Column>
        </Grid.Row>
        <Grid.Row>
          <Grid.Column textAlign="center">
            <Button size="huge">
              <a href="https://enquery.net">Learn More</a>
            </Button>
          </Grid.Column>
        </Grid.Row>
      </Grid>
    </Segment>
    <Segment style={{ padding: "0em" }} vertical>
      <Grid celled="internally" columns="equal" stackable>
        <Grid.Row textAlign="center">
          <Grid.Column style={{ paddingBottom: "5em", paddingTop: "5em" }}>
            <Header as="h3" style={{ fontSize: "2em" }}>
              "Cybersecurity Built on Decades of Experience"
            </Header>
            <p style={{ fontSize: "1.33em" }}>
              EnQuery’s team has decades of experience in the areas of advanced
              mathematics, cryptography, embedded and enterprise software
              development, as well as hardware designs and implementations.
            </p>
          </Grid.Column>
          <Grid.Column style={{ paddingBottom: "5em", paddingTop: "5em" }}>
            <Header as="h3" style={{ fontSize: "2em" }}>
              TAKING PRIVACY TO A NEW HORIZON
            </Header>
            <p style={{ fontSize: "1.33em" }}>
              Our enterprise development team has built a scalable,
              maintainable, and easy-to-deploy solution for batch and stream
              processing. We are attaining new heights in cryptographically
              secure systems, and we are using math—not magic—to do it.
            </p>
          </Grid.Column>
        </Grid.Row>
      </Grid>
    </Segment>
    <Segment style={{ padding: "9em 0em" }} vertical>
      <Container text textAlign="center">
        <Divider
          as="h4"
          className="header"
          horizontal
          style={{ margin: "3em 0em", textTransform: "uppercase" }}
        >
          <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Running-README.md">
            End-to-End examples
          </a>
        </Divider>
        <Header as="h3" style={{ fontSize: "2em" }}>
          UI Overview
        </Header>
        <Container
          textAlign="left"
          style={{ width: "400px", padding: "1em 0 0 110px" }}
        >
          <List ordered>
            <List.Item as={Link} to="/createqueryschema">
              Create Query Schema
            </List.Item>
            <List.Item as={Link} to="/createquery">
              Create Query
            </List.Item>
            <List.Item as={Link} to="/schedulequery">
              Schedule Query
              <List.List>
                <List.Item as="a">Choose parameters</List.Item>
                <List.Item>
                  <List.Icon name="calendar alternate" />
                  Pick a date
                </List.Item>
              </List.List>
            </List.Item>
            <List.Item as={Link} to="/querystatus">
              Query Status
              <List.Content>
                <List.Description>
                  Status page for scheduled queries
                </List.Description>
              </List.Content>
            </List.Item>
            <List.Item as={Link} to="/offline">
              Offline Mode
              <List.Content>
                <List.Description>
                  Ability to do this process while not connected to the
                  responder
                </List.Description>
              </List.Content>
            </List.Item>
          </List>
        </Container>
        <Button as="a" size="large" style={{ marginTop: "2em" }}>
          Further Explanation
        </Button>
      </Container>
    </Segment>
    <Segment inverted vertical style={{ padding: "5em 0em" }}>
      <Container>
        <Grid divided inverted stackable>
          <Grid.Row>
            <Grid.Column width={3}>
              <Header inverted as="h4" content="About" />
              <List link inverted>
                <List.Item>
                  <a href="https://enquery.net/contact-us">Contact Us</a>
                </List.Item>
                <List.Item>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/README.md">
                    Documentation
                  </a>
                </List.Item>
                <List.Item>
                  <a href="https://github.com/En-Query/EncryptedQuery">
                    GitHub
                  </a>
                </List.Item>
                <List.Item>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Release-Notes.md">
                    Release Notes
                  </a>
                </List.Item>
              </List>
            </Grid.Column>
            <Grid.Column width={3}>
              <Header inverted as="h4" content="Services" />
              <List link inverted>
                <List.Item as="a">FAQ</List.Item>
                <List.Item as="a">How To Access</List.Item>
              </List>
            </Grid.Column>
            <Grid.Column width={7}>
              <Header as="h4" inverted>
                EnQuery
              </Header>
              <p>Copyright (C) 2018 EnQuery LLC</p>
            </Grid.Column>
          </Grid.Row>
        </Grid>
      </Container>
    </Segment>
  </ResponsiveContainer>
);

export default HomepageLayout;
