import React, { Component } from "react";
import {
  Segment,
  Divider,
  List,
  Container,
  Header,
  Table
} from "semantic-ui-react";
import PageHeading from "./FixedMenu";

function Versions() {
  return (
    <div>
      <PageHeading />
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center"
        }}
      >
        <div
          style={{
            width: "700px"
          }}
        >
          <Header
            style={{ padding: "30px 0", fontSize: "3rem", marginTop: "2em" }}
          >
            Encrypted Query Versions
            <Header.Subheader style={{ marginTop: "20px", fontSize: "1rem" }}>
              This product is still in its early stages so new versions are
              released often. Sometimes minor changes, sometimes very major
              changes. Please check the docs/release notes of the latest
              versioning before using
            </Header.Subheader>
          </Header>
          <Table style={{ marginTop: "50px" }} celled>
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell
                  colSpan="3"
                  style={{ fontSize: "1.5rem", paddingLeft: ".4em" }}
                >
                  Current Version (Stable)
                </Table.HeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              <Table.Row>
                <Table.Cell>2.1.4</Table.Cell>
                <Table.Cell>
                  <a href="https://github.com/En-Query/EncryptedQuery#new-features">
                    Release Notes
                  </a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
            </Table.Body>
          </Table>

          <Table celled>
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell
                  colSpan="3"
                  style={{ fontSize: "1.5rem", paddingLeft: ".4em" }}
                >
                  Past Versions
                </Table.HeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              <Table.Row>
                <Table.Cell>2.1.3</Table.Cell>
                <Table.Cell>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Release-Notes.md#encrypted-query-v213---release-notes">
                    Release Notes
                  </a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
              <Table.Row>
                <Table.Cell>2.1.2</Table.Cell>
                <Table.Cell>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Release-Notes.md#encrypted-query-v212---release-notes">
                    Release Notes
                  </a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
              <Table.Row>
                <Table.Cell>2.1.1</Table.Cell>
                <Table.Cell>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Release-Notes.md#changes-in-release-211">
                    Release Notes
                  </a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
              <Table.Row>
                <Table.Cell>2.1.0</Table.Cell>
                <Table.Cell>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Release-Notes.md#new-with-release-210">
                    Release Notes
                  </a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
              <Table.Row>
                <Table.Cell>2.0.1</Table.Cell>
                <Table.Cell>
                  <a href="https://github.com/En-Query/EncryptedQuery/blob/master/Release-Notes.md#release-201-snapshot">
                    Release Notes
                  </a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
              <Table.Row>
                <Table.Cell>1</Table.Cell>
                <Table.Cell>
                  <a href="#">Release Notes</a>
                </Table.Cell>
                <Table.Cell>
                  <a href="#">Documentation</a>
                </Table.Cell>
              </Table.Row>
            </Table.Body>
          </Table>
        </div>
      </div>
    </div>
  );
}

export default Versions;
