# OpenSearch Traffic Gateway

## Table of Contents
1. [Overview](#overview)
2. [Local Testing](#local-testing)

## Overview

The OpenSearch Traffic Gateway implements an observability and governance proxy that can sit in front of your OpenSearch cluster and provide additional observability and governance features. Specifically, it can (1) log all requests (and optionally responses) in a format that can be re-ingested into OpenSearch for analysis, and (2) block certain queries based on configurable governance rules (for example, block all queries with no time range filter on a specific index).

Note: This project is a work in progress. Feedback is greatly appreciated via GitHub issues. Currently the only supported deployment method is to build from source, with steps for local testing provided below. Additional deployment instructions and options are coming soon.

## Local Testing

The steps below describe how to deploy the proxy locally using the provided Helm chart.

### Prerequisites

- [Docker](https://docs.docker.com/engine/install/)
- [Minikube](https://minikube.sigs.k8s.io/docs/start)
- [Helm](https://helm.sh/docs/intro/install/)

### Step-by-Step Instructions

1. Run the [OpenSearch docker-compose.yml file](docker/opensearch/docker-compose.yml) included in this repo (taken from [here](https://opensearch.org/docs/latest/install-and-configure/install-opensearch/docker/#sample-docker-compose-file-for-development)).
    - `export OPENSEARCH_INITIAL_ADMIN_PASSWORD=<strong-password>`
    - `docker compose -f docker/opensearch/docker-compose.yml up -d`
    - Test dashboards login with user `admin` and password specified above at `localhost:5601`
    - Test API access with: `curl -XGET http://127.0.0.1:9200/_cat/indices -u 'admin:$OPENSEARCH_INITIAL_ADMIN_PASSWORD' --insecure`
1. Start Minikube: `minikube start --driver=docker --force-systemd=true`
1. Build the proxy server docker image and load it to minikube.
    - `./gradlew :proxy-server:build`
    - `docker build -t opensearch-traffic-gateway -f docker/opensearch-traffic-gateway/Dockerfile .`
    - `docker image save -o opensearch-traffic-gateway.tar opensearch-traffic-gateway`
    - `minikube image load opensearch-traffic-gateway.tar`
1. Install the helm chart: `helm install -f kubernetes/local.values.yaml opensearch-traffic-gateway ./kubernetes/opensearch-traffic-gateway`
1. Expose the service to localhost via Minikube. In a new terminal window, run: `minikube service opensearch-traffic-gateway --url`
    - Copy the URL printed to the terminal
1. Test: `curl -XGET <COPIED URL>/_cat/indices -u 'admin:$OPENSEARCH_INITIAL_ADMIN_PASSWORD' --insecure`
1. You should be able to see the captured traffic in the proxy log: `kubectl logs -l app.kubernetes.io/name=opensearch-traffic-gateway`

### Upgrade
To upgrade an existing deployment follow these steps:
1. Build the proxy server docker image and load it to minikube.
    - `./gradlew :proxy-server:build`
    - `docker build -t opensearch-traffic-gateway -f docker/opensearch-traffic-gateway/Dockerfile .`
    - `docker image save -o opensearch-traffic-gateway.tar opensearch-traffic-gateway`
    - `minikube image load opensearch-traffic-gateway.tar`
1. Upgrade the helm chart: `helm upgrade -f kubernetes/local.values.yaml opensearch-traffic-gateway ./kubernetes/opensearch-traffic-gateway`

### Cleanup
Follow these steps to clean up the deployment when you are done.
1. Uninstall the helm chart: `helm uninstall opensearch-traffic-gateway`
1. Tear down the OpenSearch deployment: `docker-compose -f docker/opensearch/docker-compose.yml down`
1. Tear down minikube: `minikube delete --all`

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

