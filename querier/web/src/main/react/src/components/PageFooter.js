import React, { Component } from "react";
import { Grid, Segment, Container, List, Header } from "semantic-ui-react";
import { Link } from "react-router-dom";

export default class PageFooter extends Component {
  render() {
    return (
      <div style={{ marginTop: "20px" }}>
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
                <Grid.Column width={3}>
                  <Header inverted as="h4" content="Pages" />
                  <List link inverted>
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
      </div>
    );
  }
}
