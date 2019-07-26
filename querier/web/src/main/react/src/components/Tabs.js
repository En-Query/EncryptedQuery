import React from "react";
import { Tab, Dropdown, Form } from "semantic-ui-react";

const defaultDropdownOptions = {
  scrolling: true,
  clearable: true,
  fluid: true,
  selection: true,
  search: true,
  required: true,
  multiple: false,
  noResultsMessage: "Try a different Search"
};

export const Tab1Content = () => {
  //upload file button
};

export const ExportSchedulesDropdowns = ({
  dropdownOptionsDataSchema,
  dropdownOptionsQuerySchema,
  dropdownOptionsQuery
}) => {
  return (
    <Tab.Pane>
      <Form>
        <Form.Field required>
          <label>Select a Data Schema</label>
          <Dropdown
            {...{ ...defaultDropdownOptions, ...dropdownOptionsDataSchema }}
          />
        </Form.Field>
        <Form.Field required>
          <label>Select a Query Schema</label>
          <Dropdown
            {...{ ...defaultDropdownOptions, ...dropdownOptionsQuerySchema }}
          />
        </Form.Field>
        <Form.Field required>
          <label>Select a Query</label>
          <Dropdown
            {...{ ...defaultDropdownOptions, ...dropdownOptionsQuery }}
          />
        </Form.Field>
      </Form>
    </Tab.Pane>
  );
};

export const Tab3Content = () => {
  //import results
};
