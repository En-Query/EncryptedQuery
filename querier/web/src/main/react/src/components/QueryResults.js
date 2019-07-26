import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import localforage from "localforage";

import { Divider, Table, Segment, Button } from "semantic-ui-react";

import axios from "axios";
import format from "date-fns/format";

import PageFooter from "./PageFooter";
import PageHeading from "./FixedMenu";

import _ from "lodash";

const indexTuple = source =>
  source.reduce((prev, [k, v]) => ((prev[k] = v), prev), {});

class QueryResults extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      results: [],
      retrievalUris: {},
      decryptionUris: {},
      column: null,
      direction: null
    };
  }

  async componentDidMount() {
    localforage
      .getItem("decryptionUriStore")
      .then(localforageStore => {
        // This code runs once the value has been loaded
        // from the offline store.
        console.log("localforage store in cDM ---> ", localforageStore);
        this.setState({ decryptionUris: localforageStore || {} });
      })
      .catch(
        error => console.log("Error getting store from localforage --->", error)
        // This code runs if there were any errors
      );
    await this.getResultsList();
    await this.getResultsData();
    this.interval = setInterval(() => this.getResultsList(), 8000);
  }

  componentWillUnmount() {
    clearInterval(this.interval);
  }

  getResultsList = async () => {
    //fetch basic result(s) list
    try {
      const resultsUri = localStorage.getItem("resultsUri");
      console.log(
        "Initial vlaue for resultsUri in getResultsList: ",
        resultsUri
      );
      const { data } = await axios({
        method: "get",
        url: `${resultsUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });

      console.log("data.data", data.data);
      const results = data.data;
      this.setState({ results: results });

      console.log("This is the list of results from getResultsList: ", results);
    } catch (error) {
      console.error(Error(`Error fetching results list: ${error.message}`));
    }
  };

  getResultsData = async () => {
    // fetch selfUri's of all available results
    const { results } = this.state;

    console.log("-----------------------------------------------------------");
    console.log("These are the results list inside getResultsData: ", results);
    console.log("-----------------------------------------------------------");

    const fetchSelfUri = ({ id, selfUri }) =>
      axios({
        method: "get",
        url: selfUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      }).then(({ data }) => [[id], data.data.retrievalsUri]);

    const fetchResultsSelfUri = ({ id, selfUri }) =>
      axios({
        method: "get",
        url: selfUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      }).then(({ data }) => [[id], data.data]);

    try {
      const retrievalUriList = await Promise.all(results.map(fetchSelfUri));
      const resultsSelfUriList = await Promise.all(
        results.map(fetchResultsSelfUri)
      );

      const retrievalUris = indexTuple(retrievalUriList);
      const resultObjects = indexTuple(resultsSelfUriList);

      console.log(
        "These are the retrievalUri's from each result",
        retrievalUris
      );
      console.log(
        "These are the objects returned from resultSelfUri requests",
        resultObjects
      );

      this.setState({ retrievalUris, resultObjects });
    } catch (error) {
      console.error(error);
    }
  };

  getDecryptionUris = async id => {
    /* 
    Handle case where we come into this page and the result is already downloaded outside of the component.
    This will cause the sendRetrieval function to be skipped because there will be
    no download button, thus not getting the decryptionUri to render the Decrpyt button.
    */

    const retrievalsUri = this.state.retrievalUris[id];
    console.log(`Getting ${this.state.retrievalUris[id]}`);

    try {
      const { data } = await axios({
        method: "get",
        url: retrievalsUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      const decryptionsUri = data.data[0].decryptionsUri;
      console.log("decryptionsUri is ----->", decryptionsUri);

      this.setState(
        prevState => {
          prevState.decryptionUris[id] = decryptionsUri;
          return {
            decryptionUris: prevState.decryptionUris
          };
        },
        () => {
          console.log("prevState call back", this.decryptionUris);
        }
      );
      localforage
        .setItem("decryptionUriStore", this.state.decryptionUris)
        .then(response => {
          console.log("Store in getDecryptionUris() ", response);
        })
        .catch(error => {
          console.error("Error setting object to localforage ---> ", error);
        });
      console.log("State after localforage ", this.state);
      console.log("Decrypting: ", decryptionsUri);
      const response = await axios({
        method: "post",
        url: decryptionsUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      const decryptionResponse = response.data.data;
      console.log("Decryption response -->", decryptionResponse);
    } catch (error) {
      console.error("getDecryptionUris error", error);
    }
  };

  sendRetrieval = async id => {
    // handle download button that will send the post retrieval
    const retrievalsUri = this.state.retrievalUris[id];

    console.log(`Posting to ${retrievalsUri}`);

    try {
      const { data } = await axios({
        method: "post",
        url: retrievalsUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });

      const decryptionsUri = data.data.decryptionsUri;

      console.log("decryptionsUri is: ", decryptionsUri);
      console.log(this.state);

      this.setState(prevState => {
        prevState.decryptionUris[id] = decryptionsUri;
        return {
          decryptionUris: prevState.decryptionUris
        };
      });
      localforage
        .setItem("decryptionUriStore", this.state.decryptionUris)
        .then(response => {
          console.log("Response in sendRetrieval of the store --->>", response);
        })
        .catch(error => console.log("SendRetrieval localforage error", error));
    } catch (error) {
      console.error(error);
    }
  };

  sendDecryption = id => {
    //Handle Decrypt Action

    const decryptionsUri = this.state.decryptionUris[id];

    console.log(decryptionsUri);

    axios({
      method: "post",
      url: decryptionsUri,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    });
  };

  handleSort = clickedColumn => () => {
    const { column, queries, direction } = this.state;

    if (column !== clickedColumn) {
      this.setState({
        column: clickedColumn,
        data: _.sortBy(queries, [clickedColumn]),
        direction: "ascending"
      });
      return;
    }

    this.setState({
      data: queries.reverse(),
      direction: direction === "ascending" ? "descending" : "ascending"
    });
  };

  renderButtonView = (idx, status) => {
    const { id } = this.state.results[idx];
    const retrievalsUri = this.state.retrievalUris[id];
    const decryptionsUri = this.state.decryptionUris[id];

    switch (status) {
      case `Ready`:
        return (
          <button onClick={() => this.sendRetrieval(id)} type="button">
            DOWNLOAD
          </button>
        );
      case `Downloading`:
        return <button> DOWNLOADING ... </button>;
      case `Downloaded`:
        return (
          <button onClick={() => this.getDecryptionUris(id)} type="button">
            DECRYPT
          </button>
        );
      case `Decrypting`:
        return <button> DECRYPTING ...</button>;
      case `InProgress`:
        return <button> INPROGRESS</button>;
      case `Decrypted`:
        return <button> DECRYPTED </button>;
      default:
        return null;
    }
  };

  render() {
    const { results, direction, column } = this.state;
    const { match, location, history } = this.props;

    return (
      <div
        style={{ display: "flex", minHeight: "100vh", flexDirection: "column" }}
      >
        <PageHeading />
        <div style={{ flex: 1 }}>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              alignItems: "center",
              flex: "1"
            }}
          >
            <div style={{ width: "1100px" }}>
              <Segment style={{ padding: "7em 1em" }} vertical>
                <Divider horizontal>Retrievals & Results</Divider>
                <Table sortable compact celled>
                  <Table.Header>
                    <Table.Row>
                      <Table.HeaderCell
                        sorted={column === "id" ? direction : null}
                        onClick={this.handleSort("id")}
                      >
                        Query Id
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "start" ? direction : null}
                        onClick={this.handleSort("start")}
                      >
                        Start
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "end" ? direction : null}
                        onClick={this.handleSort("end")}
                      >
                        End
                      </Table.HeaderCell>
                      <Table.HeaderCell>Type</Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={column === "status" ? direction : null}
                        onClick={this.handleSort("status")}
                      >
                        Status
                      </Table.HeaderCell>
                      <Table.HeaderCell>Action</Table.HeaderCell>
                    </Table.Row>
                  </Table.Header>
                  <Table.Body>
                    {Object.values(results).map(
                      (
                        { id, type, windowStartTime, windowEndTime, status },
                        idx
                      ) => {
                        return (
                          <Table.Row>
                            <Table.Cell>{id}</Table.Cell>
                            <Table.Cell>
                              {format(
                                windowStartTime,
                                "MMMM Do YYYY, h:mm:ss A"
                              )}
                            </Table.Cell>
                            <Table.Cell>
                              {format(windowEndTime, "MMMM Do YYYY, h:mm:ss A")}
                            </Table.Cell>
                            <Table.Cell>{type}</Table.Cell>
                            <Table.Cell>{status}</Table.Cell>
                            <Table.Cell>
                              {this.renderButtonView(idx, status)}
                            </Table.Cell>
                          </Table.Row>
                        );
                      }
                    )}
                  </Table.Body>
                </Table>
              </Segment>
            </div>
          </div>
        </div>
        <PageFooter />
      </div>
    );
  }
}

export default withRouter(QueryResults);
