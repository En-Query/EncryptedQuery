import _ from "lodash";
import React, { Component, createRef } from "react";
import {
  Grid,
  Header,
  Image,
  Rail,
  Ref,
  Segment,
  Sticky,
  Container,
  List
} from "semantic-ui-react";

import { HashLink as Link } from "react-router-hash-link";
import styled from "styled-components";

import PageHeading from "./FixedMenu";
import PageFooter from "./PageFooter";

const StyledLink = styled(Link)`
  text-decoration: none;

  &:focus,
  &:hover,
  &:visited,
  &:link,
  &:active {
    text-decoration: none;
  }
`;

const Placeholder = () => (
  <Image src="https://react.semantic-ui.com/images/wireframe/paragraph.png" />
);

const Docs = () => (
  <React.Fragment>
    <Header style={{ padding: "10px 0", fontSize: "2rem" }}>Overview</Header>
    <p style={{ marginTop: "5px", fontSize: "1rem" }}>
      This product is still in its early stages so new versions are released
      often. Sometimes minor changes, sometimes very major changes.
    </p>
    <p style={{ fontSize: "1.2em" }}>
      <b>
        Please check the docs/release notes of the latest version(s) before
        using this product.
      </b>
    </p>
    <List ordered style={{ marginTop: "20px" }}>
      <List.Item>
        <Link activeStyle={{ color: "red" }} to="overview#create-query-schema">
          Create Query Schema
        </Link>
      </List.Item>
      <List.Item>
        <Link to="overview#create-query">Create Query</Link>
      </List.Item>
      <List.Item>
        <Link to="overview#query-status">Query Status</Link>
        <List.List>
          <List.Item>
            <Link to="overview#schedule-query">Schedule Query</Link>
          </List.Item>
          <List.Item>View Schedules</List.Item>
          <List.Item>View Results</List.Item>
        </List.List>
      </List.Item>
      <List.Item>
        <Link to="overview#offline">Offline Mode</Link>
      </List.Item>
    </List>
    <Header id="motivation" style={{ padding: "10px 0", fontSize: "2rem" }}>
      Motivation
    </Header>
    <p style={{ fontSize: "1rem" }}>
      Encrypted Query is designed to allow a user to query a remote database
      without revealing the contents of the query or the results from the
      database server. This is accomplished using techniques from Private
      Information Retrieval (PIR) with Paillier encryption.{" "}
    </p>
    <p style={{ fontSize: "1em" }}>
      Encrypted Query has two distinct sides - the Querier and the Responder.
      <List animated bulleted style={{ padding: "20px" }}>
        <List.Item>
          The Querier prepares encrypted queries which are then submitted to the
          Responder.
        </List.Item>
        <List.Item>
          The Responder accepts encrypted queries and executes them on selected
          data sources without revealing the criteria contained in the query, or
          the data that is returned.
        </List.Item>
      </List>
      All records of the selected data source are scanned during query
      execution.
    </p>
    <Header
      id="create-query-schema"
      style={{ padding: "30px 0", fontSize: "2rem" }}
    >
      Create Query Schema
      <Header.Subheader style={{ fontSize: "1.2rem" }}>
        BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH
        BLAH BLAH BLAH BLAH BLAH BLAH BLAH BLAH
      </Header.Subheader>
    </Header>
    <Header id="create-query" style={{ fontSize: "2rem" }}>
      Create Query
      <Header.Subheader style={{ fontSize: "1.2rem" }}>
        {" "}
        RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT
        RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT
        RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT
        RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT RANDOM TEXT
        RANDOM TEXT RANDOM TEXT{" "}
      </Header.Subheader>
    </Header>
    <Header id="query-status" style={{ fontSize: "2rem" }}>
      Query Status
    </Header>
    <p style={{ fontSize: "1.2rem" }}>
      some information on query schema process
    </p>
    <Header id="schedule-query" style={{ fontSize: "2rem" }}>
      Schedule Query
      <Header.Subheader style={{ fontSize: "1.2rem" }} />
    </Header>
    <Header id="query-schedules-status" style={{ fontSize: "2rem" }}>
      Query Schedules Status
      <Header.Subheader style={{ fontSize: "1rem" }} />
    </Header>
    <Header id="query-results" style={{ fontSize: "2rem" }}>
      Query Results
      <Header.Subheader style={{ fontSize: "1rem" }} />
    </Header>
    <Header id="offline-mode" style={{ fontSize: "2rem" }}>
      Offline Mode
      <Header.Subheader style={{ fontSize: "1rem" }} />
    </Header>
  </React.Fragment>
);

export default class Overview extends Component {
  contextRef = createRef();

  render() {
    return (
      <div
        style={{ display: "flex", minHeight: "100vh", flexDirection: "column" }}
      >
        <PageHeading />
        <div style={{ flex: 1 }}>
          <Grid centered columns={3} style={{ marginTop: "3em" }}>
            <Grid.Column>
              <Ref innerRef={this.contextRef}>
                <Container>
                  <Docs />

                  <Rail position="left">
                    <Sticky offset={90} context={this.contextRef}>
                      <Header as="h3">Getting Started</Header>
                      <List style={{ padding: "5px" }}>
                        <List.Item>Overview</List.Item>
                        <List.Item>Examples</List.Item>
                        <List.Item>Tutorial</List.Item>
                      </List>
                      <Header as="h3">End to End Query Creation</Header>
                      <List style={{ padding: "5px" }}>
                        <List.Item>
                          <a href="#createqueryschema">Create Query Schema</a>
                        </List.Item>
                        <List.Item>
                          <a href="#createquery">Create Query</a>
                        </List.Item>
                        <List.Item>
                          <a href="#querystatus">Query Status</a>
                        </List.Item>
                      </List>
                      <Header as="h3">How To</Header>
                      <List style={{ padding: "5px" }}>
                        <List.Item>
                          <a
                            target="_blank"
                            href="https://github.com/En-Query/EncryptedQuery/blob/master/doc/Building-README.md#building-the-project"
                          >
                            Building
                          </a>
                        </List.Item>
                        <List.Item>Standalone</List.Item>
                        <List.Item>Deployment</List.Item>
                      </List>
                      <Header as="h3">Development</Header>
                      <List style={{ padding: "5px" }}>
                        <List.Item>Road Map</List.Item>
                        <List.Item>Upcoming Features</List.Item>
                      </List>
                    </Sticky>
                  </Rail>

                  <Rail position="right">
                    <Sticky offset={90} context={this.contextRef}>
                      <List style={{ padding: "5px" }}>
                        <List.Item>
                          <Link smooth to="overview#motivation">
                            Motivation
                          </Link>
                        </List.Item>
                        <List.Item>
                          <StyledLink smooth to="overview#create-query-schema">
                            Create Query Schema
                          </StyledLink>
                        </List.Item>
                        <List.Item>
                          <Link smooth to="overview#create-query">
                            Create Query
                          </Link>
                        </List.Item>
                        <List.Item>
                          <Link smooth to="overview#query-status">
                            Query Status
                          </Link>
                          <List.List>
                            <List.Item>
                              <Link to="overview#schedule-query">
                                Schedule Query
                              </Link>
                            </List.Item>
                            <List.Item>
                              <Link smooth to="overview#query-schedules-status">
                                Query Schedules Status
                              </Link>
                            </List.Item>
                            <List.Item>
                              <Link smooth to="overview#query-results">
                                Query Results
                              </Link>
                            </List.Item>
                          </List.List>
                        </List.Item>
                        <List.Item>
                          <Link smooth to="overview#offline-mode">
                            Offline Mode
                          </Link>
                        </List.Item>
                      </List>
                    </Sticky>
                  </Rail>
                </Container>
              </Ref>
            </Grid.Column>
          </Grid>
        </div>
        <PageFooter />
      </div>
    );
  }
}
