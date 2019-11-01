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
  Card,
  Modal,
  Header,
  Icon
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
      selectorValue: "",
      selectorValues: [],
      isLoading: true,
      filterExpression: "",
      open: false,
      errorMessage: "",
      errorMessages: []
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


  handleQueryNameChange = (e, { value }) => {
    this.setState({ queryName: value }, () => {
      console.log("QueryName entered ---> ", this.state.queryName);
    });
  };

  handleFilterExpressionChange = (e, { value }) => {
    this.setState({ filterExpression: value }, () => {
      console.log(
        "Filter expression entered --> ",
        this.state.filterExpression
      );
    });
  };

  closeConfigShow = (closeOnEscape, closeOnDimmerClick) => () => {
    this.setState({ closeOnEscape, closeOnDimmerClick, open: true });
  };

  close = () => this.setState({ open: false });

  handleSubmit = async e => {
    e.preventDefault();
    const {
      queryName,
      selectorValues,
      filterExpression
    } = this.state;
    console.log(this.state);
    const queriesUri = localStorage.getItem("queriesUri");

    //new async/await to handle errors
    try {
      // attempt request
      const { data } = await axios({
        method: "post",
        url: `${queriesUri}`,
        headers: {
          Accept: "application/vnd.encryptedquery.enclave+json; version=1",
          "Content-Type": "application/json"
        },
        data: JSON.stringify({
          name: queryName,
          selectorValues: selectorValues,
          filterExpression:
            filterExpression.length > 0 ? filterExpression : undefined
        })
      });
      //push to next page if successful?
      this.props.history.push("/querystatus");
    } catch (error) {
      if (error.response) {
        // The request was made and the server responded with statuse code that falls outside of the 2xx range.
        // console.log(error.response.data);
        console.log(error.response);
        console.log(error.response.status);
        console.log(error.response.headers);

        let errorMessages = error.response.data.errors.map(error => {
          return {
            value: { title: error.title, detail: error.detail }
          };
        });

        console.log("multiple error messages -->", errorMessages);

        this.setState({ errorMessages: errorMessages });

        // this.setState(
        //   { errorMessage: error.response.data.errors[0].detail },
        //   () => {
        //     console.log(
        //       "error state update call back",
        //       this.state.errorMessage
        //     );
        //   }
        // );
      } else if (error.request) {
        // Request was made but no response was recieved.
        console.log(error.request);
      } else {
        //something happened in setting up the request and triggered an error
        console.log("Error", error.message);
      }
    }
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
      selectorValues,
      filterExpression,
      closeOnDimmerClick,
      open,
      errorMessage,
      errorMessages
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
                  {errorMessages && (
                    <div>
                      {errorMessages.map(value => {
                        return (
                          <Message negative>
                            <Message.Header>{value.value.title}</Message.Header>
                            {value.value.detail}
                          </Message>
                        );
                      })}
                    </div>
                  )}
                  <Form.Field>
                    <label>SQL Filter Expression:</label>

                    <div style={{ display: "flex" }}>
                      <Input
                        value={value}
                        name="filterExpression"
                        onChange={this.handleFilterExpressionChange}
                        placeholder="Enter a SQL Filter Expression"
                      />
                      <Modal
                        trigger={
                          <Button
                            icon="info"
                            size="mini"
                            onClick={this.closeConfigShow(true, false)}
                            circular
                            type="button"
                            style={{ margin: "5px" }}
                          />
                        }
                        open={open}
                        onClose={this.close}
                        closeOnDimmerClick={closeOnDimmerClick}
                        size="small"
                        centered={false}
                        basic
                      >
                        <Header icon="file" content="CURRENTLY SUPPORTED SQL" />
                        <Modal.Content>
                          <h4
                          >{`Arithmetic Operators: '+', '-', '*', '/', '%' `}</h4>
                          <h4>String concatenation: '||'</h4>
                          <h4
                          >{`Relational: '>', '>=', '<', '<=' ,'=' , '<>'`}</h4>
                          <h4>Logical operators: And, Or, Not</h4>
                          <h4>
                            "Is Empty" and "Is Not Empty" (for lists and
                            strings)
                          </h4>
                          <h4>
                            "Is Null" and "Is not null" to test null values
                          </h4>
                          <h4>"Is" and "Is not" for equality check</h4>
                          <h4>
                            "MATCHES" to test regular expressions => (should be
                            renamed to avoid confusion with SQL homonym Should
                            be changed to mimic Oracle’s REGEXP_LIKE, or
                            PostgreSQL’s REGEXP_MATCHES, since there is no
                            equivalent in ANSI SQL
                          </h4>
                          <br />
                          <h4>Operator "IN" for Strings and Lists</h4>
                          <h4>
                            Temporal values: CURRENT_TIMESTAMP, CURRENT_DATE,
                            TIMESTAMP ‘2001-07-04T14:23Z’, DATE ‘2001-07-04’
                          </h4>
                          <br />
                          <h4>
                            Lists (Row Value Constructors): (1,2,4), ('a', 'b',
                            'c')
                          </h4>
                          <br />
                          <h4>
                            Aggregate functions: AVG, SUM, MIN, MAX, COUNT
                          </h4>
                          <h4>Math functions: SQRT</h4>
                          <h4>
                            String functions: CHAR_LENGTH, CHARACTER_LENGTH
                          </h4>
                          <h4>Examples:</h4>
                          <h4>quantity is 0</h4>
                          <h4>age > 24</h4>
                          <h4>{`(price > 24) And (count < 100)`}</h4>
                          <h4> "last name" = 'Smith'</h4>
                          <h4>CURRENT_TIMESTAMP > dob</h4>
                          <h4> {`CURRENT_TIMESTAMP > '2001-07-04T14:23Z'`}</h4>
                          <h4> 'sarah' IN children</h4>
                          <h4> Not children Is Empty</h4>
                          <h4> name MATCHES 'chaper[ ]+[0-9]+'</h4>
                          <h4>(1, 3, 5) IS NOT EMPTY</h4>
                          <h4> 1 IN (1, 2, 3)</h4>
                          <h4>COUNT(3, 5.0, 18) = 3</h4>
                          <br />
                          <h3>MISSING FUNCTIONS (ANSI SQL):</h3>
                          <h4>==================================</h4>
                          <h4
                          >{`POSITION <left paren> <character value expression> IN <character value expression> <right paren>`}</h4>
                          <h4
                          >{`OCTET_LENGTH <left paren> <string value expression> <right paren>`}</h4>
                          <h4
                          >{`BIT_LENGTH <left paren> <string value expression> <right paren>`}</h4>
                          <h4
                          >{`EXTRACT <left paren> <extract field> FROM <extract source> <right paren>`}</h4>
                          <h4>SUBSTRING</h4>
                          <h4>LOWER</h4>
                          <h4>CONVERT</h4>
                          <h4>TRANSLATE</h4>
                          <h4>TRIM</h4>
                          <h4>CURRENT_TIME</h4>
                          <h4>NULLIF</h4>
                          <h4>COALESCE</h4>
                          <h4>CASE</h4>
                          <h4>CAST</h4>
                          <h4>Internal Values (temporal)</h4>
                          <h4>Arithmetic with temporal values</h4>
                          <h4>BETWEEN</h4>
                          <h4>LIKe</h4>
                          <h4>OVERLAPS</h4>
                          <h4>==================================</h4>
                        </Modal.Content>
                        <Modal.Actions>
                          <Button
                            floated="right"
                            color="red"
                            onClick={this.close}
                            inverted
                          >
                            <Icon name="close" /> Close
                          </Button>
                        </Modal.Actions>
                      </Modal>
                    </div>
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
