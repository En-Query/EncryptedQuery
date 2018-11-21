var chalk = require('chalk');

function debug(content) {
  var outputString = typeof content === 'string' ? content : JSON.stringify(content, null, 4);
  console.log(chalk.cyan(outputString));
}

var Log = {
  debug: debug
};

var requestInterceptor;
var responseInterceptor;

function initiateDebug(axios) {
  if (requestInterceptor) axiosInstance.interceptors.eject(requestInterceptor);
  if (responseInterceptor) axiosInstance.interceptors.eject(responseInterceptor);

  requestInterceptor = axios.interceptors.request.use(function (config) {
    Log.debug('Request:');
    Log.debug(config);
    return config;
  }, function (error) {
    Log.debug('Error:');
    Log.debug(error);
    return Promise.reject(error);
  });

  responseInterceptor = axios.interceptors.response.use(function (response) {
    Log.debug('Response:');
    Log.debug(response);
    return response;
  }, function (error) {
    Log.debug('Error:');
    Log.debug(error);
    return Promise.reject(error);
  });
}

module.exports = initiateDebug;
