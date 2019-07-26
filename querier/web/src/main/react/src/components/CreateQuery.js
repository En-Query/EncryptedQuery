import React, { Component } from "react";
import PropTypes from "prop-types";
import { withRouter } from "react-router";
import {
  Dropdown,
  Form,
  Button,
  Input,
  Segment,
  Divider,
  Loader,
  Message,
  TextArea,
  List,
  Container,
  Popup,
  Card
} from "semantic-ui-react";
import PageHeading from "./FixedMenu";
import PageFooter from "./PageFooter";

import axios from "axios";
require("axios-debug")(axios);

const embededOptions = [
  { key: "true", text: "True", value: "true" },
  { key: "false", text: "False", value: "false" }
];

class CreateQuery extends Component {
  constructor(props) {
    super(props);

    this.state = {
      dataschemas: [],
      queryName: "",
      dataSchemaName: "",
      queryschemas: [],
      selectorField: "",
      queriesUri: "",
      dataChunkSize: 3,
      selectorValue: "",
      selectorValues: [],
      isLoading: true
    };
  }

  addSelectorValue = e => {
    e.stopPropagation();
    e.preventDefault();
    this.setState(
      ({ selectorValues, selectorValue }) => ({
        selectorValues: [
          ...selectorValues,
          ...selectorValue
            .replace(/\r?\n|\r/g, "")
            .split(",")
            .map(str => str.trim().replace(/^"(.*)"$/, "$1"))
        ],
        selectorValue: ""
      }),
      () => {
        console.log("selectorValues ---> ", this.state.selectorValues);
      }
    );
  };

  removeSelectorValue(index) {
    this.setState({
      selectorValues: this.state.selectorValues.filter((_, i) => i !== index)
    });
  }

  handleSelectorValueChange = ({ target: { value } }) => {
    //makes separate copy of array.
    this.setState({ selectorValue: value });
  };

  async componentDidMount() {
    await this.getSchemas();
    await this.handleSchemaChange();
    await this.getQuerySchemas();
    await this.handleQuerySchemaChange();
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
        this.setState(
          {
            queryschema: queryschema,
            selectorField: queryschema.selectorField,
            queriesUri: queryschema.queriesUri
          },
          () => {
            console.log("queryschema ---> ", queryschema);
            console.log("selectorField ---> ", this.state.selectorField);
            console.log("queriesUri --->", this.state.queriesUri);
          }
        );
        localStorage.setItem("queriesUri", this.state.queriesUri);
      } catch (error) {
        console.error(
          Error(`Error getting specific querySchema: ${error.message}`)
        );
      }
    }
  };

  handleChange = (e, { value }) => {
    this.setState({ dataChunkSize: value }, () => {
      console.log("dataChunkSize ---> ", this.state.dataChunkSize);
    });
  };

  handleQueryNameChange = (e, { value }) => {
    this.setState({ queryName: value }, () => {
      console.log("QueryName entered ---> ", this.state.queryName);
    });
  };

  handleSubmit = e => {
    e.preventDefault();
    const {
      queryName,
      dataChunkSize,
      selectorValues
    } = this.state;
    console.log(this.state);
    const queriesUri = localStorage.getItem("queriesUri");
    axios({
      method: "post",
      url: `${queriesUri}`,
      headers: {
        Accept: "application/vnd.encryptedquery.enclave+json; version=1",
        "Content-Type": "application/json"
      },
      data: JSON.stringify({
        name: queryName,
        parameters: {
          dataChunkSize: dataChunkSize
        },
        selectorValues: selectorValues
      })
    })
      .then(response => {
        console.log(response);
      })
      .catch(error => console.log(error.response));
    this.props.history.push("/querystatus");
  };

  render() {
    const {
      dataschemas,
      queryschemas,
      queryName,
      dataSchemaName,
      querySchemaName,
      selectorField,
      value,
      dataChunkSize,
      selectorValues
    } = this.state;

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
            <div style={{ width: "900px" }}>
              <Form onSubmit={this.handleSubmit}>
                <Segment style={{ padding: "5em 1em" }} vertical>
                  <Divider horizontal>Query Creation Information</Divider>
                  <Form.Field required>
                    <label>Query Name: </label>
                    <Input
                      placeholder="Enter query Name"
                      name="queryName"
                      value={value}
                      onChange={this.handleQueryNameChange}
                      required
                    />
                  </Form.Field>
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
                  <Form.Field>
                    <label>SelectorField used to create the query schema</label>
                    <Input
                      fluid
                      readOnly
                      multiple={false}
                      value={selectorField}
                    />
                  </Form.Field>
                </Segment>
                <Segment style={{ padding: "5em 1em" }} vertical>
                  <Divider horizontal>RUN PARAMETERS</Divider>
                  <Form.Field required>
                    <label>Data Chunk Size:</label>

                    <div style={{ display: "flex" }}>
                      <Input
                        value={dataChunkSize}
                        onChange={this.handleChange}
                        type="number"
                        min="1"
                        required
                      />
                      <Popup
                        trigger={
                          <Button
                            icon="info"
                            size="mini"
                            circular
                            style={{ margin: "5px" }}
                          />
                        }
                        content="Description about what size does"
                      />
                    </div>
                  </Form.Field>
                  <Form.Field required>
                    <label>Selector Values:</label>
                    <TextArea
                      type="text"
                      placeholder="Enter selector values 1-by-1 or as a comma seperated list."
                      value={this.state.selectorValue}
                      onChange={this.handleSelectorValueChange}
                      required={!this.state.selectorValues.length}
                    />
                    <Button color="blue" fluid onClick={this.addSelectorValue}>
                      Add Selector Value(s)
                    </Button>
                    <ul>
                      {this.state.selectorValues.map((value, index) => {
                        return (
                          <Card>
                            <Card.Content>
                              {value}
                              <Button
                                size="mini"
                                compact
                                floated="right"
                                basic
                                color="red"
                                onClick={this.removeSelectorValue.bind(
                                  this,
                                  index
                                )}
                              >
                                X
                              </Button>
                            </Card.Content>
                          </Card>
                        );
                      })}
                    </ul>
                  </Form.Field>
                  <Form.Button
                    fluid
                    positive
                    content="Submit Query"
                    style={{ marginTop: "80px" }}
                  />
                </Segment>
              </Form>
            </div>
          </div>
        </div>
        <PageFooter />
      </div>
    );
  }
}

export default CreateQuery;
