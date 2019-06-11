# Talk to an app which which can create and manage your kubernetes instances

>A Kubernetes Assistant which can manage your Kubernetes clusters on IBM Cloud through voice

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

# Architecture flow

<p align="center">
  <img src="docs/doc-images/arch-flow.png">
</p>

1. The user triggers a login into their IBM Cloud accounts on the Android App, if not previously setup.
2. They are redirected to the IBM Cloud login page on their phone browsers, using the IBM IAM OpenID Connect Protocol.
3. After successful authentication, a request is initiated to get a generic IAM token.
4. The request sends the Authorization Code retrieved from Step 2 to the IBM IAM OpenID Connect Protocol to get the generic IAM token.
5. A request is initiated to get the list of accounts associated with the cloud login.
6. The request sends the generic IAM access token to the IBM Account Management API to retrieve the list of accounts associated with the user's cloud login. The user selects an account from the list.
7. A request is initiated to get an IAM token for the selected account.
8. The request sends the generic IAM refresh token and the selected account ID to the IBM IAM OpenID Connect Protocol to retrieve an IAM token linked to the selected cloud account.
9. The IAM Authorization Token object is persisted on the user's device for future use, until expiration.
10. A request is initiated to get the list of resource groups for the selected account.
11. The request sends the account specific IAM access token to the IBM Resource Controller API to retrieve the list of resource groups associated with the account. The user selects a resource group from the list.
12. A request is initiated to get a Watson Assistant session.
13. The request talks to the Watson Assistant API SDK through the NodeJS server to get a new assitant session.
14. The Android application setup is completed and the view is changed so that the user can start talking with the applicaton.
15. The user sends a speech command to the application, by clicking the mic button and talking.
16. The speech is converted to text using the native Android speech-2-text converter.
17. A fresh IAM token request is issued using the previous IAM refresh token if the IAM access token has reached expiration.
18. The fresh IAM access token/non-expired IAM access token, along with the user text input is sent to the NodeJS backend server on hosted on a Kubernetes cluster on the IBM cloud.
19. The user text input is sent to the Watson Assistant to extract knowledge around the intent of the text and the different entities present.
20. If the context for the user input is complete and all knowledge required to execute the Kubernetes command is obtained, a request is sent to the Kubernetes service API on IBM Cloud along with the user IAM access token and other parameters, to execute the command.
21. The result of the executed command/a follow up question to get further details around the user request is sent to the Android application.
22. The text received by the application is converted to speech by using the native Android text-2-speech converter.
23. The speech is relayed to the user to continue the conversation.

# Included components
*	[IBM Watson Assistant](https://cloud.ibm.com/catalog/services/watson-assistant) Watson Assistant lets you build conversational interfaces into any application, device, or channel.
*	[IBM Cloud Kubernetes Service](https://www.ibm.com/cloud/container-service) gcreates a cluster of compute hosts and deploys highly available containers. A Kubernetes cluster lets you securely manage the resources that you need to quickly deploy, update, and scale applications.
* [IBM Identity and Access Management](https://www.ibm.com/security/identity-access-management) Explore silent identity and access management solutions for today's hybrid environments.

## Featured technologies
+ [Node.js](https://nodejs.org) is an open source, cross-platform JavaScript run-time environment that executes server-side JavaScript code.
+ [Express.js](https://expressjs.com/) is a minimal and flexible Node.js web application framework that provides a robust set of features for web and mobile applications.
+ [Axios](https://www.npmjs.com/package/axios) Promise based HTTP client for the browser and node.js.
+ [Android](https://developer.android.com/) Android is a mobile operating system developed by Google.
+ [AppAuth-Android](https://github.com/openid/AppAuth-Android) Android client SDK for communicating with OAuth 2.0 and OpenID Connect providers.
+ [Docker](https://www.docker.com/) independent container platform that enables organizations to seamlessly build, share and run any application, anywhere—from hybrid cloud to the edge.

## Running the application

Follow these steps to set up and run this code pattern. The steps are described in detail below.

### Prerequisites

- [IBM Cloud account](https://cloud.ibm.com/registration/?target=%2Fdashboard%2Fapps)
- [Node v8.x or greater and npm v5.x or greater](https://nodejs.org/en/download/)
- [Android Studio](https://developer.android.com/studio)

### Steps

1. [Clone the repo](#1-clone-the-repo)
2. [Setup IBM IAM](#2-setup-ibm-iam)
3. [Create IBM Cloud services](#3-create-ibm-cloud-services)
4. [Configure Watson Assistant](#4-configure-watson-assistant)
5. [Deploy NodeJS server to Kubernetes](#5-deploy-nodejs-server-to-kubernetes)
6. [Configure Android Application](#6-configure-android-application)
7. [Run the application](#7-run-the-application)


## 1. Clone the repo

Clone this repository in a folder your choice:

```bash
git clone https://github.com/ash7594/kubernetes-speech-assistant.git
cd kubernetes-speech-assistant
```

## 2. Setup IBM IAM

Create IAM steps coming...

## 3. Create IBM Cloud services

* Create the [IBM Cloud Kubernetes Service](https://cloud.ibm.com/catalog/infrastructure/containers-kubernetes).  You can find the service in the `Catalog`.  For this code pattern, we can use the `Free` cluster, and give it a name.  Note, that the IBM Cloud allows one instance of a free cluster and expires after 30 days.

<br>
<p align="center">
  <img src="docs/doc-gifs/kubernetes-create.gif">
</p>
<br>

* Create the [IBM Watson Assistant Service](https://cloud.ibm.com/catalog/services/watson-assistant) service on the IBM Cloud.  You can find the service in the `Catalog`, and give a name.

<br>
<p align="center">
  <img src="docs/doc-gifs/assistant-create.gif">
</p>
<br>

## 4. Configure Watson Assistant

* Go to your provisioned Watson Assistant service on IBM Cloud. Create new credentials and enter them into the config.

<br>
<p align="center">
  <img src="docs/doc-gifs/create-ibm-kubernetes-service.gif">
</p>
<br>

* Click on `Launch Watson Assistant`.

<br>
<p align="center">
  <img src="docs/doc-gifs/create-ibm-kubernetes-service.gif">
</p>
<br>

* Create a skill using the existing json in the repository.

<br>
<p align="center">
  <img src="docs/doc-gifs/create-ibm-kubernetes-service.gif">
</p>
<br>

* Create an Assistant, and add the skill to the Assistant.

<br>
<p align="center">
  <img src="docs/doc-gifs/create-ibm-kubernetes-service.gif">
</p>
<br>

* Get the Assistant ID and enter it into the config file.

<br>
<p align="center">
  <img src="docs/doc-gifs/create-ibm-kubernetes-service.gif">
</p>
<br>

## 5. Deploy NodeJS server to Kubernetes

Steps coming

## 6. Configure Android Application

Steps coming...

## 7. Run the application

Steps coming...


## Links
* [IBM Kubernetes REST Api](https://containers.cloud.ibm.com/swagger-api/#/)
* [IBM Code Patterns for Kubernetes](https://developer.ibm.com/patterns/category/containers/)

## License
This code pattern is licensed under the Apache Software License, Version 2. Separate third-party code objects invoked within this code pattern are licensed by their respective providers pursuant to their own separate licenses. Contributions are subject to the [Developer Certificate of Origin, Version 1.1 (DCO)](https://developercertificate.org/) and the [Apache Software License, Version 2](https://www.apache.org/licenses/LICENSE-2.0.txt).

[Apache Software License (ASL) FAQ](https://www.apache.org/foundation/license-faq.html#WhatDoesItMEAN)
