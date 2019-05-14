var express = require("express");
var bodyParser = require('body-parser');
var sender = require('request');
const util = require('util');
const ibmcloudApi = require('./ibmcloud_containers_api.js');

var assistantv2 = require('watson-developer-cloud/assistant/v2'); // watson sdk
var config = require('./config.json');

var log4js = require('log4js');
var logger = log4js.getLogger();
logger.level = 'debug';
logger.debug("launching talk to kubernetes pattern");

var port = process.env.PORT || 8080;

var app = express();

app.use(function(req, res, next) {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept');
  next();
});

app.use(bodyParser.json({limit: '50mb', extended: true}));

var assistant = new assistantv2({
    version: config.version,
    iam_apikey: config.apikey,
    url: config.url,
    headers: {"X-Watson-Learning-Opt-Out": true}
});

var sessionid;

var newContext = {
  global: {
    system: {
      turn_count: 1
    }
  }
};

app.get('/session', function(req, res) {
  logger.debug('-- session');
  sessionFn(req, res);
});

app.post('/message', function(req, res) {
  logger.debug('-- message');
  messageFn(req, res);
});

function getWatsonPayload(req) {

  var contextWithAcc = (req.body.context) ? req.body.context : newContext;

  if (req.body.context) {
    contextWithAcc.global.system.turn_count += 1;
  }

  var textIn = '';

  textIn = req.body.input;

  console.log('input text:' + textIn)

  var mysession = req.body.session;

  var payload = {
    assistant_id: config.id,
    session_id: mysession,
    context: contextWithAcc,
    input: {
      message_type: 'text',
      text: textIn,
      options: {
        return_context: true
      }
    }
  };

  console.log(payload)

  return payload;
}

function sessionFn(req, res) {

  console.log('calling session endpoint');

  assistant.createSession({
    assistant_id: config.id,
  }, function(err, response) {
    if (err) {
      console.error(err);
      res.status(500).send(JSON.stringify({
        status: "assistant session error"
      }, null, 3));
    } else {
      logger.debug('established watson assistant session')
      logger.debug('session id: ' + response.session_id);

      res.status(200).send(JSON.stringify({
        session: response.session_id
      }, null, 3));
    }
  });
}

function messageFn(req, res) {

  logger.debug("hit watson message endpoint");

  var payload = getWatsonPayload(req);

  logger.debug("sending message to watson assistant")

  // Send the input to the assistant service
  assistant.message(payload, function(err, data) {

    if (err) {
      console.error(err);
      
      return res.status(500).send(JSON.stringify({
        status: "assistant message error"
      }, null, 3));
    }

    logger.debug("received response from watson assistant")

    console.log(JSON.stringify(data));

    processAssistantResponse(data, req, res);
  });
}

function returnResponse(returnObj, res) {
    console.log(JSON.stringify(returnObj));
    return res.status(200).send(JSON.stringify(returnObj, null, 3));
}

function returnResponseWithoutLog(returnObj, res) {
    return res.status(200).send(JSON.stringify(returnObj, null, 3));
}

function processAssistantResponse(data, req, res) {
  logger.debug("Entering process assistant response");
  
  var intents = data.output.intents;
  var convContext = req.body.conversationContext;

  if ((intents.length == 0 || intents.length > 1) && !convContext) {
    logger.debug("ERROR: No or multiple intents");
    return returnResponse({
      text: data.output.generic[0].text 
    }, res);
  }

  if ((intents.length>0 && intents[0].intent == "Create_Cluster") || convContext == "Create_Cluster") {
    createClusterIntent(data, req, res);
  } else if ((intents.length>0 && intents[0].intent == "Get_Cluster") || convContext == "Get_Cluster") {
    getClusterIntent(data, req, res);
  } else if ((intents.length>0 && intents[0].intent == "Delete_Cluster") || convContext == "Delete_Cluster") {
    deleteClusterIntent(data, req, res);
  } else {
    return returnResponse({
      text: data.output.generic[0].text,
      context: "",
      input: ""
    }, res);
  }
}

function createClusterIntent(data, req, res) {
  logger.debug("Entering create cluster intent");

  var resText = data.output.generic[0].text;
  var entities = data.output.entities;
  var context = data.context.skills["main skill"].user_defined.conversation_context;

  if (resText == "Okay, creating your final cluster") {
    ibmcloudApi.clusterCreateStandard(buildClusterCreateParams("standard", req, data));
    return returnResponse({
      text: "Okay, provisioning your standard cluster",
      context: "",
      input: ""
    }, res);
  }

  if (resText == "Okay, creating a free cluster") {
    ibmcloudApi.clusterCreateFree(buildClusterCreateParams("free", req, data));
    return returnResponse({
      text: "Okay, provisioning your free cluster",
      context: "",
      input: ""
    }, res);
  }

  if (context.status == "pending") {
    logger.debug("follow-up question");
    return returnResponse({
      text: resText,
      context: context.type,
      input: req.body.input
    }, res);
  }

  if (context.status == "error") {
    logger.debug("follow-up error question");
    return returnResponse({
      text: resText,
      context: context.type,
    }, res);
  }

  req.body.input = req.body.prevInput + ' ' + resText;
  var payload = getWatsonPayload(req);
  
  logger.debug("sending message to watson assistant")

  // Send the input to the assistant service
  assistant.message(payload, function(err, newdata) {

    if (err) {
      console.error(err);
    }

    logger.debug("received response from watson assistant")

    console.log(JSON.stringify(newdata));

    createClusterIntent(newdata, req, res);
  });

}

function buildClusterCreateParams(clusterType, req, watsonData) {
  logger.debug("In build cluster create params");
  
  var params = {};

  params.headers = {};
  params.headers.auth = req.body.authorization;
  params.headers.resourceGroup = req.body.resourceGroup;

  var entities = watsonData.output.entities;

  for (var i=0; i<entities.length; i++) {
    var entity = entities[i].entity;
    var value = entities[i].value;

    if (entity == "cluster_name_value") {
      params.name = value;
      break;
    } 
  }
  
  if (clusterType == "free") {
    logger.debug("params:");
    console.log(JSON.stringify(params));
    logger.debug("Create free cluster: Init...");
    
    return params;
  }

  // standard cluster

  var numbers = [];
  var machineTypeData = [];

  for (var i=0; i<entities.length; i++) {
    var entity = entities[i].entity;
    var value = entities[i].value;

    if (entity == "cluster_region") {
      params.headers.region = value;
    } else if (entity == "sys-number") {
      numbers.push(entities[i].metadata.numeric_value);
    } else if (entity == "cluster_core" || entity == "cluster_ram") {
      machineTypeData.push(entity);
    }
  }

  params.cores = (machineTypeData[0] == "cluster_core") ? numbers[0] : numbers[1];
  params.memory = (machineTypeData[0] == "cluster_ram") ? numbers[0] : numbers[1];
    
  logger.debug("params:");
  console.log(JSON.stringify(params));
  logger.debug("Create standard cluster: Init...");
    
  return params;
}

function getClusterIntent(data, req, res) {
  logger.debug("Entering get cluster intent");

  var resText = data.output.generic[0].text;
  var entities = data.output.entities;
  var context = data.context.skills["main skill"].user_defined.conversation_context;

  if (resText == "Okay, here are the clusters you requested") {
    return ibmcloudApi.clusterGet(
      buildClusterGetParams(req, data),
      buildClusterGetResponse,
      failureCb,
      res);
  }

  if (context.status == "pending") {
    logger.debug("follow-up question");
    return returnResponse({
      text: resText,
      context: context.type,
      input: req.body.input
    }, res);
  }

  if (context.status == "error") {
    logger.debug("follow-up error question");
    return returnResponse({
      text: resText,
      context: context.type,
    }, res);
  }

  req.body.input = req.body.prevInput + ' ' + resText;
  var payload = getWatsonPayload(req);
  
  logger.debug("sending message to watson assistant")

  // Send the input to the assistant service
  assistant.message(payload, function(err, newdata) {

    if (err) {
      console.error(err);
    }

    logger.debug("received response from watson assistant")

    console.log(JSON.stringify(newdata));

    getClusterIntent(newdata, req, res);
  });

}

function buildClusterGetParams(req, watsonData) {
  logger.debug("In build cluster get params");
  
  var params = {};

  params.headers = {};
  params.headers.auth = req.body.authorization;
  params.headers.resourceGroup = req.body.resourceGroup;

  var entities = watsonData.output.entities;

  for (var i=0; i<entities.length; i++) {
    var entity = entities[i].entity;
    var value = entities[i].value;

    if (entity == "cluster_region") {
      params.headers.region = value;
      break;
    }
  }

  logger.debug("params:");
  console.log(JSON.stringify(params));
  logger.debug("Get cluster: Init...");
    
  return params;
}

function buildClusterGetResponse(data, res, region) {
  var numClusters = data.length;
  
  return returnResponseWithoutLog({
    text: `You currently have ${numClusters} clusters`,
    data: data,
    context: "",
    input: ""
  }, res);
}

function buildClusterGetResponseForDeletion(data, res, context, input, region) {
  return returnResponseWithoutLog({
    text: "Which cluster do you want to delete? Say the number",
    data: data,
    context: context.type,
    input: input,
    region: region
  }, res);
}

function failureCb(res) {
  return returnResponse({
    text: "Error occurred",
    context: "",
    input: ""
  }, res);
}

function deleteClusterIntent(data, req, res) {
  logger.debug("Entering delete cluster intent");

  var resText = data.output.generic[0].text;
  var entities = data.output.entities;
  var context = data.context.skills["main skill"].user_defined.conversation_context;

  if (resText == "Okay, getting resources to delete cluster") {
    return ibmcloudApi.clusterGet(
      buildClusterGetParams(req, data),
      function(resData, oRes, region) { return buildClusterGetResponseForDeletion(resData, oRes, context, req.body.input, region) },
      failureCb,
      res);
  }
  
  if (resText == "Got number to delete") {
    return ibmcloudApi.clusterDelete(
      buildClusterDeleteParams(req, data),
      buildClusterDeleteResponse,
      failureCb,
      res);
  }

  if (context.status == "pending") {
    logger.debug("follow-up question");
    return returnResponse({
      text: resText,
      context: context.type,
      input: req.body.input
    }, res);
  }

  if (context.status == "error") {
    logger.debug("follow-up error question");
    return returnResponse({
      text: resText,
      context: context.type,
      data: req.body.clusterList
    }, res);
  }

  req.body.input = req.body.prevInput + ' ' + resText;
  var payload = getWatsonPayload(req);
  
  logger.debug("sending message to watson assistant")

  // Send the input to the assistant service
  assistant.message(payload, function(err, newdata) {

    if (err) {
      console.error(err);
    }

    logger.debug("received response from watson assistant")

    console.log(JSON.stringify(newdata));

    deleteClusterIntent(newdata, req, res);
  });

}

function buildClusterDeleteParams(req, watsonData) {
  logger.debug("In build cluster delete params");
  
  var params = {};

  params.headers = {};
  params.headers.auth = req.body.authorization;
  params.headers.resourceGroup = req.body.resourceGroup;
  params.headers.region = req.body.region;

  var entities = watsonData.output.entities;

  for (var i=0; i<entities.length; i++) {
    var entity = entities[i].entity;
    var value = entities[i].value;

    if (entity == "sys-number") {
      var num = entities[i].metadata.numeric_value;
      var index = num - 1;

      params.clusterId = req.body.clusterList[index].id;
      params.clusterName = req.body.clusterList[index].name;
      break;
    }
  }

  logger.debug("params:");
  console.log(JSON.stringify(params));
  logger.debug("Delete cluster: Init...");
    
  return params;
}

function buildClusterDeleteResponse(data, res, clusterName) {
  return returnResponseWithoutLog({
    text: `Cluster ${clusterName} is successfully deleted`,
    data: data,
    context: "",
    input: ""
  }, res);
}

app.listen(port);
logger.debug("Listening on port ", port);
