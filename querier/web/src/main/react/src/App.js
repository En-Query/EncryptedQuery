import React, { Component } from "react";
import { withRouter } from "react-router-dom";
import logo from "./logo.svg";
import Routes from "./routes";

import CreateQuerySchema from "./components/CreateQuerySchema";
import CreateQuery from "./components/CreateQuery";
import ScheduleQuery from "./components/ScheduleQuery";
import QueryStatus from "./components/QueryStatus";
import QueryResults from "./components/QueryResults";
import QuerySchedulesStatus from "./components/QuerySchedulesStatus";

import {
  BrowserRouter,
  Router,
  Route,
  Switch,
  Redirect
} from "react-router-dom";

export default () => <Routes />;
