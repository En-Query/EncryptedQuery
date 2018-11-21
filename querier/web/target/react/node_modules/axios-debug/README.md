# Axios Debug

[![npm version](https://badge.fury.io/js/axios-debug.svg)](https://badge.fury.io/js/axios-debug)

A convenient way to view the requests and responses when using [axios](https://github.com/mzabriskie/axios).

## Installation

```bash
npm i axios-debug
```

## Usage

```javascript
// Axios must be available.
import axios from 'axios';

// Pass axios to the imported 'axios-debug' function.
require('axios-debug')(axios);

// Use axios as normal.
axios.get('/cats');
```
