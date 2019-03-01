import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import createHistory from "history/createBrowserHistory";

import LogoSection from "./logo-section.js";
import CreateQuerySchema from "./CreateQuerySchema";
import CreateQuery from "./CreateQuery";
import VerticalNavBar from "./NavigationBar.js";

import "../css/QueryStatus.css";

import axios from "axios";

class QueryStatus extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataSchemas: [],
      dataSchemaId: [],
      dataSchemaUri: [],
      dataSchemaName: [],
      querySchemas: [],
      querySchemaUri: [],
      querySchemaId: [],
      querySchemaName: [],
      queryNames: [],
      queryStatus: [],
      queries: [],
      querySelfUri: []
    };

    this.dataSchemaChange = this.dataSchemaChange.bind(this);
    this.scheduleQuery = this.scheduleQuery.bind(this);
    this.viewSchedules = this.viewSchedules.bind(this);
    this.scheduleAgain = this.scheduleAgain.bind(this);
  }

  //invoke data fetch function inside interval timeout as well as outside of it
  componentDidMount() {
    axios({
      method: "get",
      url: `/querier/api/rest/dataschemas`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        console.log(response);
        this.setState({ dataSchemas: response.data.data });
      })
      .catch(error => console.log(error.response));
  }

  //prevent memory leak
  componentWillUnmount() {
    clearTimeout(this.timeout);
  }

  dataSchemaChange = e => {
    const dataSchema = this.state.dataSchemas.find(
      dataSchema => dataSchema.name === e.target.value
    );
    if (dataSchema) {
      axios({
        method: "get",
        url: `${dataSchema.selfUri}/queryschemas`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      })
        .then(response => {
          console.log(response);
          console.log(JSON.stringify(dataSchema.selfUri));
          console.log(dataSchema.id);
          this.setState(
            {
              querySchemas: response.data.data,
              querySchemaSelfUri: response.data.data[0].selfUri,
              selectedId: dataSchema.id
            },
            () => {
              console.log(this.state.querySchemas);
              console.log(this.state.querySchemaSelfUri);
              console.log(this.state.selectedId);
            }
          );
        })
        .catch(error => console.log(error.response));
    }
  };

  querySchemaChange = e => {
    const querySchema = this.state.querySchemas.find(
      querySchema => querySchema.name === e.target.value
    );
    if (querySchema) {
      const { id, name, selfUri } = querySchema;
      this.setState(
        {
          querySchemaId: id,
          querySchemaName: name,
          querySchemaSelfUri: selfUri
        },
        () => {
          console.log(this.state.querySchemaId);
          this.getQueryData();
        }
      );
    }
  };

  getQueryData = () => {
    axios({
      method: "get",
      url: `${this.state.querySchemaSelfUri}/queries`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1"
      }
    })
      .then(response => {
        // check if status is ENCRYPTED/SCHEDULED, will stop polling for updated status
        if (response.data.data[0].status !== "Scheduled") {
          this.timeout = setTimeout(() => this.getQueryData(), 10000);
        }
        console.log(response);
        this.setState(
          {
            queries: response.data.data,
            queryId: response.data.data[0].id,
            queryName: response.data.data[0].name,
            queryStatus: response.data.data[0].status,
            querySelfUri: response.data.data[0].selfUri
          },
          () => {
            console.log(this.state.queries);
            console.log(this.state.queryId);
            console.log(this.state.queryName);
            console.log(this.state.queryStatus);
            console.log(this.state.querySelfUri);
          }
        );
      })
      .catch(error => console.log(error.response));
  };

  handleButtonView = (status, querySelfUri) => {
    switch (status) {
      case `Created`:
        return <button className="btnNoAction"> NO ACTION </button>;
      case `Encrypting`:
        return <button> ENCRYPTING ... </button>;
      case `Encrypted`:
        return (
          <button
            onClick={e => this.scheduleQuery(e, querySelfUri)}
            type="button"
          >
            SCHEDULE
          </button>
        );
      case `Scheduled`:
        return (
          <React.Fragment>
            <button
              onClick={e => this.viewSchedules(e, querySelfUri)}
              type="button"
            >
              VIEW SCHEDULES{" "}
            </button>
            <button
              onClick={e => this.scheduleAgain(e, querySelfUri)}
              type="button"
            >
              SCHEDULE AGAIN{" "}
            </button>
          </React.Fragment>
        );
      case `Failed`:
        return <button className="btnFailed"> Failed </button>;
      default:
        return null;
    }
  };

  scheduleQuery = async (e, querySelfUri) => {
    // Handle schedule button interaction
    // Only when status = ENCRYPTED, goes to Schedule Query Page.
    e.preventDefault();
    await this.setState({ querySelfUri });
    localStorage.setItem("querySelfUri", this.state.querySelfUri);
    console.log(
      "This query will be used to load info on the next page:",
      this.state.querySelfUri
    );
    this.props.history.push(`/schedulequery`);
  };

  viewSchedules = async (e, querySelfUri) => {
    // Handle view schedules button interaction
    // Only when status = SCHEDULED, goes to Query Schedules Status Page
    e.preventDefault();
    await this.setState({ querySelfUri });
    localStorage.setItem("querySelfUri", this.state.querySelfUri);
    console.log(
      "This query will be used to load info on the next page:",
      this.state.querySelfUri
    );
    this.props.history.push(`/queryschedulesstatus`);
  };

  scheduleAgain = async (e, querySelfUri) => {
    //function for  scheduling a query multiple times
    e.preventDefault();
    await this.setState({ querySelfUri });
    localStorage.setItem("querySelfUri", this.state.querySelfUri);
    console.log(
      "This query will be used to load info on the next page:",
      this.state.querySelfUri
    );
    this.props.history.push(`/schedulequery`);
  };

  render() {
    const {
      dataSchemas,
      dataSchemaId,
      querySchemas,
      querySchemaName,
      querySchema,
      queryNames,
      queryStatus,
      queries,
      query,
      status
    } = this.state;
    const {
      match: {
        params: { querySelfUri }
      }
    } = this.props;

    return (
      <div>
        <LogoSection />
        <VerticalNavBar />
        <form onSubmit={this.handleSubmit}>
          <fieldset>
            <legend>Query Status</legend>
            <div className="status-selectboxes">
              <div>
                <label>
                  Pick a DataSchema to filter down available QuerySchemas:
                </label>{" "}
                <select
                  value={this.state.value}
                  onChange={this.dataSchemaChange}
                >
                  <option value="">Choose DataSchema ...</option>
                  {dataSchemas &&
                    dataSchemas.length > 0 &&
                    dataSchemas.map(dataSchema => {
                      return (
                        <option value={dataSchema.name}>
                          {dataSchema.name}
                        </option>
                      );
                    })}
                </select>
              </div>

              <div>
                <label>
                  Pick a QuerySchema to view its corresponding queries status:
                </label>{" "}
                <select
                  value={this.state.querySchemaName}
                  onChange={this.handleChange}
                  onChange={this.querySchemaChange}
                >
                  <option value="">Choose QuerySchema ...</option>
                  {querySchemas &&
                    querySchemas.map(querySchema => {
                      return (
                        <option value={querySchema.name}>
                          {querySchema.name}
                        </option>
                      );
                    })}
                </select>
              </div>
            </div>
          </fieldset>
        </form>

        <table id="queries">
          <caption> Queries </caption>
          <tr>
            <th>Query Name</th>
            <th>Status</th>
            <th>SelffUri</th>
            <th>Action</th>
          </tr>
          <React.Fragment>
            {this.state.queries.map(query => {
              return (
                <tr>
                  <td key={query.name}>{query.name}</td>
                  <td key={query.status}>{query.status}</td>
                  <td key={query.selfUri}>{query.selfUri}</td>
                  <td>
                    {" "}
                    {this.handleButtonView(query.status, query.selfUri)}{" "}
                  </td>
                </tr>
              );
            })}
          </React.Fragment>
        </table>
      </div>
    );
  }
}

export default withRouter(QueryStatus);
