const axios = require('axios');

const CONTAINERS_BASE_URL = 'https://containers.cloud.ibm.com';

exports.clusterCreateFree = (data) => {
  return axios.create({
    baseURL: CONTAINERS_BASE_URL,
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': data.headers.auth,
      'X-Auth-Resource-Group': data.headers.resourceGroup,
      'X-Region': 'us-south'
    }
  }).post('/v1/clusters', {
    "name": data.name,
    "noSubnet": false,
    "masterVersion": "",
    "enableTrusted": false,
    "disableAutoUpdate": false,
    "machineType": "free",
    "privateVlan": "",
    "publicVlan": "",
    "dataCenter": "",
    "isolation": "",
    "workerNum": 1,
    "prefix": "",
    "diskEncryption": true
  }).then(response => {
    console.log(response.data);
  }).catch(err => {
    console.error(err);
  });
};

exports.clusterCreateStandard = (data) => {
  return axios.create({
    baseURL: CONTAINERS_BASE_URL,
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': data.headers.auth,
      'X-Auth-Resource-Group': data.headers.resourceGroup,
      'X-Region': data.headers.region
    }
  })
  .get('/v1/zones?showFlavors=true')
  .then(res => {
    if (res.data.length <= 0) {
      throw new Error("No zones available in region: " + data.headers.region);
    }

    var zone = res.data[0];
    data.dataCenter = zone.id;

    var flavors = zone.flavors;
    if (flavors.length <= 0) {
      throw new Error("No flavors available in zone: " + zone.id);
    }

    flavors.sort((a, b) => {
      a.cores = parseInt(a.cores);
      a.memory = parseInt(a.memory);

      b.cores = parseInt(b.cores);
      b.memory = parseInt(b.memory);

      if (a.cores > b.cores) return 1;
      else if (a.cores < b.cores) return -1;
      else if (a.memory > b.memory) return 1;
      else return -1;
    });

    var coresMinAbs = Math.abs(flavors[0].cores - data.cores);
    var coresClosestIndex = 0;
    for (var i=1; i<flavors.length; i++) {
      var flavor = flavors[i];
      var diff = Math.abs(flavor.cores - data.cores);
      if (coresMinAbs > diff) {
        coresMinAbs = diff;
        coresClosestIndex = i;
      }
    }

    var memMinAbs = Math.abs(flavors[coresClosestIndex].memory - data.memory);
    var memClosestIndex = coresClosestIndex;
    for (var i=coresClosestIndex; i<flavors.length && flavors[i].cores==flavors[coresClosestIndex].cores; i++) {
      var flavor = flavors[i];
      var diff = Math.abs(flavor.memory - data.memory);
      if (memMinAbs > diff) {
        memMinAbs = diff;
        memClosestIndex = i;
      }
    }

    var flavorSelected = flavors[memClosestIndex];
    
    console.log("flavor selected:");
    console.log(JSON.stringify(flavorSelected));

    data.machineType = flavorSelected.name;
    return "ok";
  })
  .then(res => {
    return axios.create({
      baseURL: CONTAINERS_BASE_URL,
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'Authorization': data.headers.auth,
        'X-Auth-Resource-Group': data.headers.resourceGroup,
        'X-Region': data.headers.region
      }
    })
    .get(`/v1/datacenters/${data.dataCenter}/vlans`)
  })
  .then(res => { 
    if (res.data.length <= 0) {
      console.log("No vlans available in zone: " + data.dataCenter);
      data.privateVlan = "";
      data.publicVlan = "";
      return "ok";
    }

    var vlans = res.data;
    for (var i=0; i<vlans.length; i++) {
      var vlan = vlans[i];
      if (vlan.type == "private") {
        data.privateVlan = vlan.id;
        break;
      }
    }
    
    for (var i=0; i<vlans.length; i++) {
      var vlan = vlans[i];
      if (vlan.type == "public") {
        data.publicVlan = vlan.id;
        break;
      }
    }

    console.log("Private Vlan: " + data.privateVlan);
    console.log("Public Vlan: " + data.publicVlan);
    return "ok";
  })
  .then(res => { 
    return axios.create({
      baseURL: CONTAINERS_BASE_URL,
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'Authorization': data.headers.auth,
        'X-Auth-Resource-Group': data.headers.resourceGroup,
        'X-Region': data.headers.region
      }
    }).post('/v1/clusters', {
      "name": data.name,
      "noSubnet": false,
      "masterVersion": "",
      "enableTrusted": false,
      "disableAutoUpdate": false,
      "defaultWorkerPoolName": "",
      "machineType": data.machineType,
      "privateVlan": data.privateVlan,
      "publicVlan": data.publicVlan,
      "dataCenter": data.dataCenter,
      "isolation": "public",
      "workerNum": 1,
      "prefix": "",
      "diskEncryption": true,
      "privateSeviceEndpoint": false,
      "publicServiceEndpoint": false
    })
  })
  .then(response => {
    console.log(response.data);
  })
  .catch(err => {
    console.error(err);
  });
};

exports.clusterGet = (data, successCb, failureCb, resCb) => {
  return axios.create({
    baseURL: CONTAINERS_BASE_URL,
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': data.headers.auth,
      'X-Auth-Resource-Group': data.headers.resourceGroup,
      'X-Region': data.headers.region
    }
  })
  .get('/v1/clusters')
  .then(res => {
    console.log(res.data.length);
    return successCb(res.data, resCb, data.headers.region);
  })
  .catch(err => {
    console.error(err);
    return failureCb(resCb);
  });
};

exports.clusterDelete = (data, successCb, failureCb, resCb) => {
  return axios.create({
    baseURL: CONTAINERS_BASE_URL,
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': data.headers.auth,
      'X-Auth-Resource-Group': data.headers.resourceGroup,
      'X-Region': data.headers.region
    }
  })
  .delete(`/v1/clusters/${data.clusterId}`)
  .then(res => {
    console.log(res.data);
    return successCb(res.data, resCb, data.clusterName);
  })
  .catch(err => {
    console.error(err);
    return failureCb(resCb);
  });
};

module.exports = exports;
