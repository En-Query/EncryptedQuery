import React from "react";
import { Message } from "semantic-ui-react";

import PageHeading from "./PageHeader";
import PageFooter from "./PageFooter";

export default class PageNotFound extends React.Component {
  render() {
    return (
      <body>
        <div
          style={{
            display: "flex",
            minHeight: "100vh",
            flexDirection: "column"
          }}
        >
          <PageHeading />
          <div style={{ flex: 1 }}>
            <Message
              header="YIKES! This page does not exist! "
              content="Oops looks like this page does not exisit. Please use the menu above to reach your desired page."
            />
          </div>
          <PageFooter />
        </div>
      </body>
    );
  }
}
