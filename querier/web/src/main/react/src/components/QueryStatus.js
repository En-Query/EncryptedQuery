import React from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router-dom";
import createHistory from "history/createBrowserHistory";
import PageHeading from "./FixedMenu";
import PageFooter from "./PageFooter";
import _ from "lodash";

import {
  Divider,
  Segment,
  Container,
  Header,
  Table,
  Form,
  Dropdown,
  Loader,
  Button
} from "semantic-ui-react";

import axios from "axios";

class QueryStatus extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      dataschemas: [],
      queryName: "",
      queryschemas: [],
      dataSchemaName: "",
      queriesUri: "",
      queries: [],
      column: null,
      direction: null,
      querySelfUri: ""
    };
  }

  //invoke data fetch function inside interval timeout as well as outside of it
  // async componentDidMount() {
  //   await this.getSchemas();
  //   await this.handleSchemaChange();
  //   await this.getQuerySchemas();
  //   await this.handleQuerySchemaChange();
  //   await this.getQueryData();
  //   this.interval = setInterval(() => this.getQueryData(), 7000);
  // }

  componentDidMount() {
    this.getData();
  }

  async getData() {
    await this.getSchemas();
    await this.handleSchemaChange();
    await this.getQuerySchemas();
    await this.handleQuerySchemaChange();
    await this.getQueryData();
    this.interval = setInterval(() => this.getQueryData(), 5000);
  }

  //prevent memory leak
  componentWillUnmount() {
    clearInterval(this.interval);
  }

  getSchemas = async () => {
    try {
      const { data } = await axios({
        method: "get",
        url: "/querier/api/rest/dataschemas",
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log(data);
      const dataschemas = data.data;

      this.setState({ dataschemas: dataschemas, isLoading: false });

      console.log("This is the dataschema list:", dataschemas);
    } catch (error) {
      console.error(Error(`Error fetching results list: ${error.message}`));
    }
  };

  handleSchemaChange = async (e, { value }) => {
    this.setState({ dataSchemaName: value }, () => {
      console.log("Chosen dataSchema ---> ", this.state.dataSchemaName);
      //also send a request the selfUri of the selected dataSchema
    });
    const schema = this.state.dataschemas.find(schema => schema.name === value);
    if (schema) {
      try {
        const { data } = await axios({
          method: "get",
          url: schema.selfUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
        console.log(data);
        this.setState({ queryschemaUri: data.data.querySchemasUri });
        console.log(
          "queryschemaUri from dataschema change ---> ",
          this.state.queryschemaUri
        );
      } catch (error) {
        console.error(
          Error(`Error fetching specific schema: ${error.message}`)
        );
      }
    }
    this.getQuerySchemas();
  };

  getQuerySchemas = async () => {
    const { queryschemaUri } = this.state;
    try {
      const { data } = await axios({
        method: "get",
        url: queryschemaUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log("Data from queryschemaUri fetch --> ", data);
      const queryschemas = data.data;
      this.setState({ queryschemas: queryschemas });
    } catch (error) {
      console.error(Error(`Error getting queryschemas: ${error.message}`));
    }
  };

  handleQuerySchemaChange = async (e, { value }) => {
    //handle queryschema dropdown change
    this.setState({ querySchemaName: value }, () => {
      console.log("Chosen querySchema ---> ", this.state.querySchemaName);
    });
    const schema = this.state.queryschemas.find(
      schema => schema.name === value
    );
    if (schema) {
      const { id, name, selfUri } = schema;
      this.setState({
        querySchemaId: id,
        querySchemaName: name,
        querySchemaSelfUri: selfUri
      });
      try {
        const { data } = await axios({
          method: "get",
          url: selfUri,
          headers: {
            Accept: "application/vnd.encryptedquery.enclave+json; version=1"
          }
        });
        console.log(
          "Specific querySchema data from selfUri request ---> ",
          data
        );
        const queryschema = data.data;
        this.setState({
          queryschema: queryschema,
          queriesUri: queryschema.queriesUri
        });
        console.log("queryschema ---> ", queryschema);
        console.log("queriesUri --->", this.state.queriesUri);
        localStorage.setItem("queriesUri", this.state.queriesUri);
      } catch (error) {
        console.error(
          Error(`Error getting specific querySchema: ${error.message}`)
        );
      }
    }
    this.getQueryData();
  };

  getQueryData = async () => {
    //get query data using queriesUri from above
    const { queriesUri } = this.state;
    try {
      const { data } = await axios({
        method: "get",
        url: queriesUri,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1"
        }
      });
      console.log("queriesUri data --> ", data.data);
      const queries = data.data;
      this.setState({
        queries: queries,
        querySelfUri: data.data[0].selfUri
      });
      console.log(this.state.querySelfUri);
    } catch (error) {
      console.error(Error(`Error getting queriesUri data: ${error.message}`));
    }
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

  handleButtonView = (status, querySelfUri) => {
    switch (status) {
      case `Created`:
        return <Button className="btnNoAction"> NO ACTION </Button>;
      case `Encrypting`:
        return <Button> ENCRYPTING ... </Button>;
      case `Encrypted`:
        return (
          <Button
            onClick={e => this.scheduleQuery(e, querySelfUri)}
            type="button"
          >
            SCHEDULE
          </Button>
        );
      case `Scheduled`:
        return (
          <React.Fragment>
            <Button
              onClick={e => this.viewSchedules(e, querySelfUri)}
              type="button"
            >
              VIEW SCHEDULES{" "}
            </Button>
            <Button
              onClick={e => this.scheduleAgain(e, querySelfUri)}
              type="button"
            >
              SCHEDULE AGAIN{" "}
            </Button>
          </React.Fragment>
        );
      case `Failed`:
        return <Button color="red"> Failed </Button>;
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
      dataschemas,
      queryschemas,
      dataSchemaName,
      querySchemaName,
      queries,
      column,
      direction
    } = this.state;
    const { match: { params: { querySelfUri } } } = this.props;

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
          <Form onSubmit={this.handleSubmit}>
            <Segment style={{ padding: "5em 1em" }} vertical>
              <Divider horizontal>Query Status</Divider>
              <Form.Field required>
                <label>Select a Data Schema</label>
                <Dropdown
                  placeholder="Select data schema"
                  scrolling
                  clearable
                  fluid
                  selection
                  search
                  noResultsMessage="Try a different Search"
                  multiple={false}
                  options={dataschemas.map(schema => {
                    return {
                      key: schema.id,
                      text: schema.name,
                      value: schema.name
                    };
                  })}
                  header="PLEASE SELECT A DATASCHEMA"
                  value={dataSchemaName}
                  onChange={this.handleSchemaChange}
                  required
                />
              </Form.Field>
              <Form.Field required>
                <label>Select a Query Schema</label>
                <Dropdown
                  placeholder="Select query schema"
                  scrolling
                  clearable
                  fluid
                  selection
                  search
                  noResultsMessage="Try a different Search"
                  multiple={false}
                  options={queryschemas.map(schema => {
                    return {
                      key: schema.id,
                      text: schema.name,
                      value: schema.name
                    };
                  })}
                  header="PLEASE SELECT A QUERYSCHEMA"
                  value={querySchemaName}
                  onChange={this.handleQuerySchemaChange}
                  required
                />
              </Form.Field>
            </Segment>
          </Form>

          <Divider horizontal>QUERIES TABLE</Divider>
          <Table sortable compact celled>
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell
                  sorted={column === "name" ? direction : null}
                  onClick={this.handleSort("name")}
                >
                  Query Name
                </Table.HeaderCell>
                <Table.HeaderCell
                  sorted={column === "id" ? direction : null}
                  onClick={this.handleSort("id")}
                >
                  Query Id
                </Table.HeaderCell>
                <Table.HeaderCell
                  sorted={column === "selfUri" ? direction : null}
                  onClick={this.handleSort("selfUri")}
                >
                  SelfUri
                </Table.HeaderCell>
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
              {Object.values(queries).map(({ name, selfUri, status, id }) => {
                return (
                  <Table.Row>
                    <Table.Cell>{name}</Table.Cell>
                    <Table.Cell>{id}</Table.Cell>
                    <Table.Cell>{selfUri}</Table.Cell>
                    <Table.Cell>{status}</Table.Cell>
                    <Table.Cell>
                      {this.handleButtonView(status, selfUri)}
                    </Table.Cell>
                  </Table.Row>
                );
              })}
            </Table.Body>
          </Table>
        </div>
        </div>
        </div>
          <PageFooter />
      </div>
    );
  }
}

export default withRouter(QueryStatus);
