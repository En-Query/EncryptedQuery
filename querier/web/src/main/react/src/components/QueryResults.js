import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import axios from "axios";
import moment from "moment";
import "../css/QueryResults.css";

import VerticalNavBar from "./NavigationBar.js";
import LogoSection from "./logo-section.js";

const indexTuple = source =>
  source.reduce((prev, [k, v]) => ((prev[k] = v), prev), {});

class QueryResults extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      results: [],
      retrievalUris: {},
      decryptionUris: {}
    };
  }

  async componentDidMount() {
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

    try {
      const retrievalUriList = await Promise.all(results.map(fetchSelfUri));
      const retrievalUris = indexTuple(retrievalUriList);

      console.log(
        "These are the retrievalUri's from each result",
        retrievalUris
      );

      this.setState({ retrievalUris });
    } catch (error) {
      console.error(error);
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

  renderButtonView = (idx, status) => {
    const { id } = this.state.results[idx];
    const retrievalsUri = this.state.retrievalUris[id];
    const decryptionsUri = this.state.decryptionUris[id];

    switch (status) {
      case `Ready`:
        if (retrievalsUri) {
          return (
            <button onClick={() => this.sendRetrieval(id)} type="button">
              DOWNLOAD
            </button>
          );
        }
        break;
      case `Downloading`:
        return <button> DOWNLOADING ... </button>;
      case `Downloaded`:
        if (decryptionsUri) {
          return (
            <button onClick={() => this.sendDecryption(id)} type="button">
              DECRYPT
            </button>
          );
        }
        break;
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
    const { results } = this.state;
    const { match, location, history } = this.props;

    return (
      <div>
        <LogoSection />
        <VerticalNavBar />
        <table id="results">
          <caption> Retrievals + Results </caption>
          <tbody>
            <tr>
              <th>result_ID</th>
              <th>Start</th>
              <th>End</th>
              <th>Type</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
            {Object.values(
              results
            ).map(
              ({ id, type, windowStartTime, windowEndTime, status }, idx) => {
                return (
                  <tr>
                    <td>{id}</td>
                    <td>
                      {moment(windowStartTime).format(
                        "MMMM Do YYYY, h:mm:ss a"
                      )}{" "}
                    </td>
                    <td>
                      {moment(windowEndTime).format(
                        "MMMM Do YYYY, h:mm:ss a"
                      )}{" "}
                    </td>
                    <td>{type}</td>
                    <td>{status}</td>
                    <td>{this.renderButtonView(idx, status)}</td>
                  </tr>
                );
              }
            )}
          </tbody>
        </table>
      </div>
    );
  }
}

export default withRouter(QueryResults);
