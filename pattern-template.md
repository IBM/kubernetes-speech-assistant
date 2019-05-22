# Short title

Kubernetes Speech Assistant powered by Watson Assistant and Kubernetes

# Long title

Manage your Kubernetes clusters using Natural Language with the Kubernetes Speech Assistant Android application

# Author

* Ashutosh Nath Agarwal <ashutosh.nath.agarwal@ibm.com>

# URLs

### Github repo

* https://github.com/ash7594/kubernetes-speech-assistant

# Summary

This code pattern is for developers looking to start building blockchain applications with Watson Assistant and the Kubernetes container service. Learn how to build an Android application using the IBM IAM OpenID and a Node.js backend server connected with Watson Assistant to build a speech assistant for Kubernetes on IBM Cloud.

# Technologies

+ [Node.js](https://nodejs.org) is an open source, cross-platform JavaScript run-time environment that executes server-side JavaScript code.
+ [Express.js](https://expressjs.com/) is a minimal and flexible Node.js web application framework that provides a robust set of features for web and mobile applications.
+ [Axios](https://www.npmjs.com/package/axios) Promise based HTTP client for the browser and node.js.
+ [Android](https://developer.android.com/) Android is a mobile operating system developed by Google.
+ [AppAuth-Android](https://github.com/openid/AppAuth-Android) Android client SDK for communicating with OAuth 2.0 and OpenID Connect providers.
+ [Docker](https://www.docker.com/) independent container platform that enables organizations to seamlessly build, share and run any application, anywhere—from hybrid cloud to the edge.

# Description

Imagine driving to work early in the morning, when you realise that you need to deploy a backend on a Kubernetes cluster which you are yet to provision. Knowing that provisioning a cluster could take several minutes after selecting the right configuration needed for your task, you visualize sitting in front of your computer staring idly at the screen, waiting for the cluster to be initialized and wishing that you could start work immediately.

Not in my world!

This code pattern demonstrates a Kubernetes speech assistant application, to which you can simply talk to in natural language, and provision, view and manage your Kubernetes clusters without having the need to do it manually on the cloud interface. The pattern showcases an Android Application which the end user would interact with, and a NodeJs backend server that holds the application logic and talks to the IBM Cloud Kubernetes service. The pattern demostrates use of the IBM IAM OpenID using the OpenID Connect specifications for a native application, and showcases use of Watson Assistant to understand the natural language spoken by the user, holding the context of speech, and converting the user intent into executable Kubernetes commands to be performed on the IBM Cloud.

When the reader has completed this code pattern, they will understand how to:

* Create an Android application connected with the OpenID Connect specifications for IBM IAM openID
* Develop a Node.js server using Express.js which interfaces with the IBM Watson Assistant and IBM Kubernetes service
* Setup Watson Assistant and create intents, entities and a dialog flow
* Convert speech to text and text to speech natively for an Android application
* Manage OpenID Connect authorization tokens on an Android application
* Deploy a Node.js backend server on the IBM Kubernetes container service

# Flow

![Architecture flow](https://github.com/ash7594/kubernetes-speech-assistant/blob/master/docs/doc-images/arch-flow.png?raw=true)

**Note** The blockchain network will have multiple members and partners

1. Member is registered on the network
2. Member can sign-in to make transactions to earn points, redeem points and view their transactions
3. Partner is registered on the network
4. Partner can sign-in to view their transactions and display dashboard

# Components and services

*	[IBM Watson Assistant](https://cloud.ibm.com/catalog/services/watson-assistant) Watson Assistant lets you build conversational interfaces into any application, device, or channel.
*	[IBM Cloud Kubernetes Service](https://www.ibm.com/cloud/container-service) gcreates a cluster of compute hosts and deploys highly available containers. A Kubernetes cluster lets you securely manage the resources that you need to quickly deploy, update, and scale applications.
* [IBM Identity and Access Management](https://www.ibm.com/security/identity-access-management) Explore silent identity and access management solutions for today's hybrid environments.
